package ru.nsu.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoteFunctionTest {
    @Mock
    private CloudClient mockClient;

    static class TestClass {
        public String toStringValue() {
            return "real";
        }
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCall() throws Exception {
        String className = "TestClass";
        String methodName = "testMethod";
        byte[] classBytes = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
        Class<Integer> returnType = Integer.class;

        RemoteFunction<Integer> remoteFunction = new RemoteFunction<>(
                mockClient, className, methodName, classBytes, returnType
        );

        when(mockClient.executeTask(any(), eq(returnType))).thenReturn(42);

        Integer result = remoteFunction.call(1, 2);

        assertEquals(42, result);
        verify(mockClient, times(1)).executeTask(any(), eq(returnType));
    }

    @Test
    void testCallAsync() throws Exception {
        String className = "TestClass";
        String methodName = "testMethod";
        byte[] classBytes = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
        Class<Integer> returnType = Integer.class;

        RemoteFunction<Integer> remoteFunction = new RemoteFunction<>(
                mockClient, className, methodName, classBytes, returnType
        );

        CompletableFuture<Integer> future = CompletableFuture.completedFuture(42);
        when(mockClient.executeTaskAsync(any(), eq(returnType))).thenReturn(future);

        CompletableFuture<Integer> result = remoteFunction.callAsync(1, 2);

        assertEquals(42, result.get(1, TimeUnit.SECONDS));
        verify(mockClient, times(1)).executeTaskAsync(any(), eq(returnType));
    }

    @Test
    void testCallAsyncWithException() {
        String className = "TestClass";
        String methodName = "testMethod";
        byte[] classBytes = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
        Class<Integer> returnType = Integer.class;

        RemoteFunction<Integer> remoteFunction = new RemoteFunction<>(
                mockClient, className, methodName, classBytes, returnType
        );

        CompletableFuture<Integer> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("boom"));

        when(mockClient.executeTaskAsync(any(), eq(returnType)))
                .thenReturn(failedFuture);

        CompletableFuture<Integer> result = remoteFunction.callAsync(1, 2);

        assertNotNull(result);
        assertTrue(result.isDone());
        assertTrue(result.isCompletedExceptionally());
    }


    @Test
    void testLoadClassBytes() throws IOException {
        Class<TestClass> clazz = TestClass.class;

        byte[] classBytes = RemoteFunction.loadClassBytes(clazz);

        assertNotNull(classBytes);
        assertTrue(classBytes.length > 0);
        assertEquals((byte) 0xCA, classBytes[0]);
        assertEquals((byte) 0xFE, classBytes[1]);
        assertEquals((byte) 0xBA, classBytes[2]);
        assertEquals((byte) 0xBE, classBytes[3]);
    }

    @Test
    void testOfWithReturnType() throws Exception {
        Class<TestClass> clazz = TestClass.class;
        String methodName = "toStringValue";
        Class<String> returnType = String.class;

        RemoteFunction<String> remoteFunction = RemoteFunction.of(
                mockClient, clazz, methodName, returnType
        );

        assertNotNull(remoteFunction);

        when(mockClient.executeTask(any(), eq(returnType))).thenReturn("test");

        String result = remoteFunction.call();

        assertEquals("test", result);
    }
}
