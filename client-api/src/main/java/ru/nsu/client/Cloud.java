package ru.nsu.client;

import java.net.URI;

/**
 * Точка входа для создания клиентов и удаленных функций
 */
public class Cloud {

    public static CloudClient createClient(String dispatcherUrl) {
        return new CloudClient(URI.create(dispatcherUrl));
    }

    public static <R> RemoteFunction<R> remoteFunction(
            CloudClient client,
            Class<?> clazz,
            String methodName,
            Class<?>... parameterTypes) throws Exception {
        return RemoteFunction.of(client, clazz, methodName, parameterTypes);
    }

    public static DistributedMap distributedMap(
            CloudClient client,
            Class<?> mapperClass,
            String mapperMethodName) throws Exception {
        byte[] classBytes = RemoteFunction.loadClassBytes(mapperClass);
        String className = mapperClass.getName();
        return new DistributedMap(client, className, mapperMethodName, classBytes, 10);
    }
}

