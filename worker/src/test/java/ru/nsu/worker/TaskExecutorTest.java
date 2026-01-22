package ru.nsu.worker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.nsu.model.Task;
import ru.nsu.model.TaskMetadata;
import ru.nsu.model.TaskResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskExecutorTest {
    private DynamicClassLoader classLoader;
    private TaskExecutor taskExecutor;

    @BeforeEach
    void setUp() {
        classLoader = new DynamicClassLoader(Thread.currentThread().getContextClassLoader());
        taskExecutor = new TaskExecutor(classLoader, 2);
    }

    @Test
    void testExecuteTaskSuccess() {
        Task task = createTestTask();

        TaskResult result = taskExecutor.executeTask(task);

        assertNotNull(result);
        assertEquals(task.getTaskId(), result.getTaskId());
    }

    @Test
    void testExecuteTaskWithInvalidClass() {
        byte[] invalidClassBytes = new byte[]{0x00, 0x01, 0x02, 0x03};
        Task task;
        try {
            task = new Task(
                    UUID.randomUUID(),
                    "InvalidClass",
                    "testMethod",
                    invalidClassBytes,
                    serializeArguments(1, 2),
                    "invalid-hash",
                    new TaskMetadata(Instant.now(), 1, 30000)
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        TaskResult result = taskExecutor.executeTask(task);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testExecuteTaskWithInvalidArguments() {
        Task task = createTestTask();
        task = new Task(
                task.getTaskId(),
                task.getClassName(),
                task.getMethodName(),
                task.getClassBytes(),
                new byte[]{0x00, 0x01, 0x02}, // Невалидная сериализация
                task.getCodeHash(),
                task.getMetadata()
        );

        TaskResult result = taskExecutor.executeTask(task);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void testExecuteTaskAsync() throws Exception {
        Task task = createTestTask();
        CountDownLatch latch = new CountDownLatch(1);
        final TaskResult[] resultHolder = new TaskResult[1];

        taskExecutor.executeTaskAsync(task, result -> {
            resultHolder[0] = result;
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(resultHolder[0]);
        assertEquals(task.getTaskId(), resultHolder[0].getTaskId());
    }

    @Test
    void testActiveTasksCounter() throws Exception {
        Task task = createTestTask();
        int initialActiveTasks = taskExecutor.getActiveTasks();

        taskExecutor.executeTaskAsync(task, result -> {
        });

        Thread.sleep(100);

        assertTrue(taskExecutor.getActiveTasks() >= initialActiveTasks);
    }

    @Test
    void testShutdown() {
        taskExecutor.shutdown();

        assertNotNull(taskExecutor);
    }

    private Task createTestTask() {
        try {
            String className = "SimpleCalculator";
            String methodName = "sum";

            byte[] classBytes = loadTestClassBytes(SimpleCalculator.class);

            byte[] arguments = serializeArguments(5, 3);

            return new Task(
                    UUID.randomUUID(),
                    className,
                    methodName,
                    classBytes,
                    arguments,
                    "test-hash",
                    new TaskMetadata(Instant.now(), 1, 30000)
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test task", e);
        }
    }

    private <T> byte[] loadTestClassBytes(Class<T> clazz) throws IOException {
        String resourceName = clazz.getName().replace('.', '/') + ".class";
        try (var inputStream = clazz.getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IOException("Class file not found: " + resourceName);
            }
            return inputStream.readAllBytes();
        }
    }

    @Test
    void testExecuteSimpleCalculator() throws Exception {
        byte[] classBytes = loadTestClassBytes(SimpleCalculator.class);

        Task task = new Task(
                UUID.randomUUID(),
                SimpleCalculator.class.getName(),
                "sum",
                classBytes,
                serializeArguments(10, 20),
                "simple-calc-hash",
                new TaskMetadata(Instant.now(), 1, 30000)
        );

        TaskResult result = taskExecutor.executeTask(task);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(task.getTaskId(), result.getTaskId());
        assertNotNull(result.getResult());

        Integer actualResult = deserializeResult(result.getResult());
        assertEquals(30, actualResult);
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeResult(byte[] resultBytes) throws Exception {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(resultBytes))) {
            return (T) ois.readObject();
        }
    }

    private byte[] serializeArguments(Object... args) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(args);
            return baos.toByteArray();
        }
    }

    class SimpleCalculator {
        public static int sum(Integer a, Integer b) {
            return a + b;
        }
    }
}
