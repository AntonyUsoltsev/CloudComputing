package ru.nsu.worker;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import ru.nsu.model.Task;
import ru.nsu.model.TaskProgress;
import ru.nsu.model.TaskResult;
import ru.nsu.model.TaskStatus;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Выполняет задачи в пуле потоков
 * Использует DynamicClassLoader для динамической загрузки кода
 * Отслеживает загруженные классы по codeHash для оптимизации
 */
@Slf4j
public class TaskExecutor {
    private final DynamicClassLoader classLoader;
    private final ExecutorService executorService;
    private final Set<String> loadedCodeHashes;
    @Getter
    private volatile int activeTasks = 0;

    public TaskExecutor(DynamicClassLoader classLoader, int threadPoolSize) {
        this.classLoader = classLoader;
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        this.loadedCodeHashes = ConcurrentHashMap.newKeySet();
    }

    /**
     * Выполняет задачу
     *
     * @param task задача для выполнения
     * @return TaskResult результат выполнения
     */
    public TaskResult executeTask(Task task) {
        return executeTask(task, null);
    }

    public void executeTaskAsync(Task task, TaskResultCallback callback, ProgressCallback progressCallback) {
        executorService.submit(() -> {
            TaskResult result = executeTask(task, progressCallback);
            callback.onComplete(result);
        });
    }

    public void executeTaskAsync(Task task, TaskResultCallback callback) {
        executeTaskAsync(task, callback, null);
    }

    private TaskResult executeTask(Task task, ProgressCallback progressCallback) {
        if (progressCallback != null) {
            progressCallback.onProgress(TaskProgress.running(task.getTaskId(), 10, "Loading class"));
        }

        activeTasks++;
        try {
            log.info("Executing task {}", task.getTaskId());

            Class<?> clazz;
            try {
                clazz = classLoader.loadClassFromBytes(task.getClassName(), task.getClassBytes(), task.getCodeHash());
                if (task.getCodeHash() != null) {
                    loadedCodeHashes.add(task.getCodeHash());
                }
                log.debug("Class {} loaded successfully (hash: {})", task.getClassName(), task.getCodeHash());
            } catch (LinkageError e) {
                log.error("Failed to load class {}: {}", task.getClassName(), e.getMessage(), e);
                return TaskResult.failure(task.getTaskId(), "Failed to load class: " + e.getMessage());
            }

            if (progressCallback != null) {
                progressCallback.onProgress(TaskProgress.running(task.getTaskId(), 30, "Deserializing arguments"));
            }

            Object[] args;
            try {
                args = deserializeArguments(task.getArguments());
                String argsString = args == null ? "" : Arrays.stream(args)
                        .map(String::valueOf)
                        .collect(Collectors.joining(" "));

                log.debug("Deserialized {} arguments: {}", args != null ? args.length : 0, argsString);
            } catch (Exception e) {
                log.error("Failed to deserialize arguments: {}", e.getMessage(), e);
                return TaskResult.failure(task.getTaskId(), "Failed to deserialize arguments: " + e.getMessage());
            }

            if (progressCallback != null) {
                progressCallback.onProgress(TaskProgress.running(task.getTaskId(), 50, "Finding method"));
            }

            Method method;
            try {
                method = findMethod(clazz, task.getMethodName(), args);
                log.debug("Found method {} with {} parameters", task.getMethodName(), args.length);
            } catch (NoSuchMethodException e) {
                log.error("Method {} not found in class {}: {}", task.getMethodName(), task.getClassName(), e.getMessage(), e);
                return TaskResult.failure(task.getTaskId(), "Method not found: " + e.getMessage());
            }

            if (progressCallback != null) {
                progressCallback.onProgress(TaskProgress.running(task.getTaskId(), 70, "Executing method"));
            }

            Object result;
            try {
                method.setAccessible(true);
                result = method.invoke(null, args);
                log.debug("Method {} executed successfully", task.getMethodName());
            } catch (Exception e) {
                log.error("Error invoking method {}: {}", task.getMethodName(), e.getMessage(), e);
                return TaskResult.failure(task.getTaskId(), "Error invoking method: " + e.getMessage());
            }

            if (progressCallback != null) {
                progressCallback.onProgress(TaskProgress.running(task.getTaskId(), 90, "Serializing result"));
            }

            byte[] resultBytes;
            try {
                resultBytes = serializeResult(result);
                log.debug("Result serialized to {} bytes", resultBytes.length);
            } catch (Exception e) {
                log.error("Failed to serialize result: {}", e.getMessage(), e);
                return TaskResult.failure(task.getTaskId(), "Failed to serialize result: " + e.getMessage());
            }

            log.info("Task {} completed successfully", task.getTaskId());
            return TaskResult.success(task.getTaskId(), resultBytes);

        } catch (Exception e) {
            log.error("Unexpected error executing task {}", task.getTaskId(), e);
            return TaskResult.failure(task.getTaskId(), "Unexpected error: " + e.getMessage());
        } finally {
            activeTasks--;
        }
    }

    public void shutdown() {
        executorService.shutdown();
    }

    public Set<String> getLoadedCodeHashes() {
        return Set.copyOf(loadedCodeHashes);
    }

    private Object[] deserializeArguments(byte[] arguments) throws Exception {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(arguments))) {
            return (Object[]) ois.readObject();
        }
    }

    private byte[] serializeResult(Object result) throws Exception {
        var baos = new java.io.ByteArrayOutputStream();
        try (var oos = new java.io.ObjectOutputStream(baos)) {
            oos.writeObject(result);
        }
        return baos.toByteArray();
    }

    private Method findMethod(Class<?> clazz, String methodName, Object[] args) throws NoSuchMethodException {
        for (Method method : clazz.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (method.getParameterCount() != args.length) {
                continue;
            }
            Class<?>[] paramTypes = method.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < args.length; i++) {
                if (!paramTypes[i].isAssignableFrom(args[i].getClass())) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return method;
            }
        }
        throw new NoSuchMethodException("Method " + methodName + " with compatible arguments not found");
    }

    @FunctionalInterface
    public interface TaskResultCallback {
        void onComplete(TaskResult result);
    }

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(TaskProgress progress);
    }
}

