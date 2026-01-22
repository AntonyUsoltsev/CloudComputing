package ru.nsu.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.nsu.common.JacksonConfig;
import ru.nsu.model.Task;
import ru.nsu.model.TaskMetadata;
import ru.nsu.model.TaskResult;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudClientTest {
    private HttpServer testServer;
    private CloudClient client;
    private int serverPort;
    private volatile String lastSubmittedTask;
    private volatile String lastRequestedTaskId;

    @BeforeEach
    void setUp() throws IOException {
        serverPort = findFreePort();
        testServer = HttpServer.create(new InetSocketAddress("localhost", serverPort), 0);

        testServer.createContext("/api/tasks/submit", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                byte[] body = exchange.getRequestBody().readAllBytes();
                lastSubmittedTask = new String(body);
                sendResponse(exchange, 200, "{\"status\":\"ok\"}");
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            }
        });

        testServer.createContext("/api/tasks/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");
                if (parts.length >= 4) {
                    lastRequestedTaskId = parts[3];
                    UUID taskId = UUID.fromString(parts[3]);

                    // Симулируем успешный результат
                    TaskResult result = TaskResult.success(taskId,
                            CloudClient.serializeArguments(42));
                    ObjectMapper mapper = JacksonConfig.createObjectMapper();
                    String json = mapper.writeValueAsString(result);
                    sendResponse(exchange, 200, json);
                } else {
                    sendResponse(exchange, 404, "{\"error\":\"Not found\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            }
        });

        testServer.start();
        client = new CloudClient(URI.create("http://localhost:" + serverPort));
    }

    @AfterEach
    void tearDown() {
        if (testServer != null) {
            testServer.stop(0);
        }
    }

    @Test
    void testSubmitTask() throws Exception {
        Task task = createTestTask();
        client.submitTask(task);

        assertNotNull(lastSubmittedTask);
        assertTrue(lastSubmittedTask.contains(task.getTaskId().toString()));
    }

    @Test
    void testGetTaskResult() throws Exception {
        UUID taskId = UUID.randomUUID();
        TaskResult result = client.getTaskResult(taskId);

        assertNotNull(result);
        assertEquals(taskId, result.getTaskId());
        assertTrue(result.isSuccess());
        assertEquals(taskId.toString(), lastRequestedTaskId);
    }

    @Test
    void testGetTaskResultNotFound() throws Exception {
        testServer.removeContext("/api/tasks/");
        testServer.createContext("/api/tasks/", exchange -> {
            sendResponse(exchange, 404, "{\"error\":\"Not found\"}");
        });

        UUID taskId = UUID.randomUUID();
        TaskResult result = client.getTaskResult(taskId);

        assertNull(result);
    }

    @Test
    void testSerializeAndDeserializeArguments() throws Exception {
        Object[] args = new Object[]{1, 2, "test"};
        byte[] serialized = CloudClient.serializeArguments(args);

        assertNotNull(serialized);
        assertTrue(serialized.length > 0);

        Object[] deserialized = CloudClient.deserializeResult(serialized, Object[].class);
        assertArrayEquals(args, deserialized);
    }

    private Task createTestTask() throws Exception {
        return new Task(
                UUID.randomUUID(),
                "TestClass",
                "testMethod",
                new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0x00, 0x00, 0x00, 0x37},
                CloudClient.serializeArguments(1, 2),
                "test-hash",
                new TaskMetadata(Instant.now(), 1, 30000)
        );
    }

    private int findFreePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}
