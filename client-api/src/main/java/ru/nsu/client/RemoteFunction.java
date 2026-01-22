package ru.nsu.client;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.nsu.model.Task;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * Обертка для удаленного вызова функци
 * Позволяет вызывать методы на удаленных узлах как локальные функции
 *
 * @param <R> тип возвращаемого значения
 */
@Slf4j
@AllArgsConstructor
public class RemoteFunction<R> {
    private final CloudClient client;
    private final String className;
    private final String methodName;
    private final byte[] classBytes;
    private final Class<R> returnType;

    public R call(Object... args) throws Exception {
        Task task = TaskBuilder.createTask(className, methodName, classBytes, args);
        return client.executeTask(task, returnType);
    }

    public CompletableFuture<R> callAsync(Object... args) {
        try {
            Task task = TaskBuilder.createTask(className, methodName, classBytes, args);
            return client.executeTaskAsync(task, returnType);
        } catch (Exception e) {
            CompletableFuture<R> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    public static <R> RemoteFunction<R> of(
            CloudClient client,
            Class<?> clazz,
            String methodName,
            Class<R> returnType) throws IOException {

        String className = clazz.getName();
        byte[] classBytes = loadClassBytes(clazz);

        return new RemoteFunction<>(client, className, methodName, classBytes, returnType);
    }

    @SuppressWarnings("unchecked")
    public static <R> RemoteFunction<R> of(
            CloudClient client,
            Class<?> clazz,
            String methodName,
            Class<?>... parameterTypes) throws IOException, NoSuchMethodException {

        Method method = clazz.getMethod(methodName, parameterTypes);
        Class<?> returnType = method.getReturnType();

        return of(client, clazz, methodName, (Class<R>) returnType);
    }

    public static byte[] loadClassBytes(Class<?> clazz) throws IOException {
        String resourceName = clazz.getName().replace('.', '/') + ".class";
        try (var inputStream = clazz.getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IOException("Class file not found: " + resourceName);
            }
            return inputStream.readAllBytes();
        }
    }
}

