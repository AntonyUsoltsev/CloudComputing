package ru.nsu.client;

import java.net.URI;

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
}

