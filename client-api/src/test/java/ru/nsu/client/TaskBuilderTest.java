package ru.nsu.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.nsu.model.Task;
import ru.nsu.model.TaskMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskBuilderTest {

    @Test
    void testCreateTaskWithClassBytes() throws IOException {
        String className = "TestClass";
        String methodName = "testMethod";
        byte[] classBytes = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0x00, 0x00, 0x00, 0x37};
        Object[] args = new Object[]{1, 2, "test"};

        Task task = TaskBuilder.createTask(className, methodName, classBytes, args);

        assertNotNull(task);
        assertEquals(className, task.getClassName());
        assertEquals(methodName, task.getMethodName());
        assertArrayEquals(classBytes, task.getClassBytes());
        assertNotNull(task.getArguments());
        assertNotNull(task.getTaskId());
        assertNotNull(task.getCodeHash());
        assertNotNull(task.getMetadata());
    }

    @Test
    void testCreateTaskWithClassFile(@TempDir Path tempDir) throws IOException {
        String className = "TestClass";
        String methodName = "testMethod";
        byte[] classBytes = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0x00, 0x00, 0x00, 0x37};
        Path classFile = tempDir.resolve("TestClass.class");
        Files.write(classFile, classBytes);
        Object[] args = new Object[]{1, 2};

        Task task = TaskBuilder.createTask(className, methodName, classFile.toString(), args);

        assertNotNull(task);
        assertEquals(className, task.getClassName());
        assertEquals(methodName, task.getMethodName());
        assertArrayEquals(classBytes, task.getClassBytes());
        assertNotNull(task.getArguments());
    }

    @Test
    void testCreateTaskWithNonExistentFile() {
        String className = "TestClass";
        String methodName = "testMethod";
        String nonExistentFile = "/non/existent/path/TestClass.class";
        Object[] args = new Object[]{1, 2};

        assertThrows(IOException.class, () -> {
            TaskBuilder.createTask(className, methodName, nonExistentFile, args);
        });
    }

    @Test
    void testCreateTaskWithEmptyArgs() throws IOException {
        String className = "TestClass";
        String methodName = "testMethod";
        byte[] classBytes = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};

        Task task = TaskBuilder.createTask(className, methodName, classBytes);

        assertNotNull(task);
        assertNotNull(task.getArguments());
    }

    @Test
    void testCreateTaskMetadata() throws IOException {
        String className = "TestClass";
        String methodName = "testMethod";
        byte[] classBytes = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
        Object[] args = new Object[]{1};

        Task task = TaskBuilder.createTask(className, methodName, classBytes, args);

        TaskMetadata metadata = task.getMetadata();
        assertNotNull(metadata);
        assertNotNull(metadata.getCreatedAt());
        assertEquals(1, metadata.getPriority());
        assertTrue(metadata.getTimeoutMs() > 0);
    }

    @Test
    void testCreateTaskGeneratesUniqueIds() throws IOException {
        String className = "TestClass";
        String methodName = "testMethod";
        byte[] classBytes = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};

        Task task1 = TaskBuilder.createTask(className, methodName, classBytes);
        Task task2 = TaskBuilder.createTask(className, methodName, classBytes);

        assertNotEquals(task1.getTaskId(), task2.getTaskId());
    }
}
