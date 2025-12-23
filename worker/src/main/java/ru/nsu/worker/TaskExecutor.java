package ru.nsu.worker;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import ru.nsu.model.Task;
import ru.nsu.model.TaskResult;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Выполняет задачи в пуле потоков.
 * Использует DynamicClassLoader для загрузки и выполнения кода.
 */
@Slf4j
public class TaskExecutor {
    private final DynamicClassLoader classLoader;
    private final ExecutorService executorService;
    @Getter
    private volatile int activeTasks = 0;

    public TaskExecutor(DynamicClassLoader classLoader, int threadPoolSize) {
        this.classLoader = classLoader;
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
    }

    /**
     * Выполняет задачу асинхронно.
     * @param task задача для выполнения
     * @return TaskResult результат выполнения
     */
    public TaskResult executeTask(Task task) {
        activeTasks++;
        try {
            log.info("Executing task {}", task.getTaskId());
            log.debug("Task details: className={}, methodName={}, classBytes.length={}, arguments.length={}", 
                    task.getClassName(), task.getMethodName(), 
                    task.getClassBytes() != null ? task.getClassBytes().length : 0,
                    task.getArguments() != null ? task.getArguments().length : 0);

            Class<?> clazz;
            try {
                clazz = classLoader.loadClassFromBytes(task.getClassName(), task.getClassBytes());
                log.debug("Class {} loaded successfully", task.getClassName());
            } catch (LinkageError e) {
                log.error("Failed to load class {}: {}", task.getClassName(), e.getMessage(), e);
                return TaskResult.failure(task.getTaskId(), "Failed to load class: " + e.getMessage());
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

            Method method;
            try {
                method = findMethod(clazz, task.getMethodName(), args);
                log.debug("Found method {} with {} parameters", task.getMethodName(), args.length);
            } catch (NoSuchMethodException e) {
                log.error("Method {} not found in class {}: {}", task.getMethodName(), task.getClassName(), e.getMessage(), e);
                return TaskResult.failure(task.getTaskId(), "Method not found: " + e.getMessage());
            }

            Object result;
            try {
                result = method.invoke(null, args);
                log.debug("Method {} executed successfully, result type: {}, result value : {}", task.getMethodName(),
                        result != null ? result.getClass().getName() : "null", result);
            } catch (Exception e) {
                log.error("Error invoking method {}: {}", task.getMethodName(), e.getMessage(), e);
                return TaskResult.failure(task.getTaskId(), "Error invoking method: " + e.getMessage());
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

    public void executeTaskAsync(Task task, TaskResultCallback callback) {
        executorService.submit(() -> {
            TaskResult result = executeTask(task);
            callback.onComplete(result);
        });
    }

    public void shutdown() {
        executorService.shutdown();
    }

    private Object[] deserializeArguments(byte[] arguments) throws Exception {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(arguments))) {
            return (Object[]) ois.readObject();
        }
    }

    private byte[] serializeResult(Object result) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos)) {
            oos.writeObject(result);
        }
        return baos.toByteArray();
    }

    private Method findMethod(Class<?> clazz, String methodName, Object[] args) throws NoSuchMethodException {
        Class<?>[] paramTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i].getClass();
        }
        return clazz.getMethod(methodName, paramTypes);
    }

    @FunctionalInterface
    public interface TaskResultCallback {
        void onComplete(TaskResult result);
    }
}

