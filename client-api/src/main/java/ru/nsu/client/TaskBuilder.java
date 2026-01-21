package ru.nsu.client;

import lombok.extern.slf4j.Slf4j;
import ru.nsu.model.Task;
import ru.nsu.model.TaskMetadata;
import ru.nsu.utils.HashUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

/**
 * Утилита для создания Task из класса и аргументов.
 */
@Slf4j
public class TaskBuilder {
    /**
     * Создает Task из скомпилированного класса и аргументов.
     */
    public static Task createTask(
            String className,
            String methodName,
            String classFilePath,
            Object... args) throws IOException {

        Path classPath = Paths.get(classFilePath);
        if (!Files.exists(classPath)) {
            throw new IOException("Class file not found: " + classFilePath);
        }

        byte[] classBytes = Files.readAllBytes(classPath);

        return createTask(className, methodName, classBytes, args);
    }

    /**
     * Создает Task из байткода класса и аргументов.
     */
    public static Task createTask(
            String className,
            String methodName,
            byte[] classBytes,
            Object... args) throws IOException {

        byte[] arguments = CloudClient.serializeArguments(args);

        String codeHash = HashUtils.calculateHash(classBytes);

        TaskMetadata metadata = new TaskMetadata(Instant.now(), 1, 30000);

        return new Task(
                UUID.randomUUID(),
                className,
                methodName,
                classBytes,
                arguments,
                codeHash,
                metadata
        );
    }
}

