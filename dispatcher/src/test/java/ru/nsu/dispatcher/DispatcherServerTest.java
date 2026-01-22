package ru.nsu.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.nsu.common.JacksonConfig;
import ru.nsu.model.Task;
import ru.nsu.model.TaskMetadata;
import ru.nsu.model.TaskResult;
import ru.nsu.model.WorkerRegistrationRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatcherServerTest {
    private DispatcherServer server;
    private int serverPort;
    private final ObjectMapper objectMapper = JacksonConfig.createObjectMapper();
    private HttpClient httpClient;
    private HttpServer mockWorkerServer;
    private int mockWorkerPort;

    @BeforeEach
    void setUp() throws IOException {
        serverPort = findFreePort();
        mockWorkerPort = findFreePort();

        mockWorkerServer = HttpServer.create(new InetSocketAddress("localhost", mockWorkerPort), 0);
        mockWorkerServer.createContext("/api/tasks/execute", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 200, "{\"status\":\"accepted\"}");
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            }
        });
        mockWorkerServer.start();

        server = new DispatcherServer(serverPort);
        server.start();
        httpClient = HttpClient.newHttpClient();

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (mockWorkerServer != null) {
            mockWorkerServer.stop(0);
        }
    }

    @Test
    void testWorkerRegistration() throws Exception {
        String workerId = "worker-1";
        URI workerAddress = URI.create("http://localhost:8081");
        WorkerRegistrationRequest request = new WorkerRegistrationRequest(workerId, workerAddress);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + "/api/workers/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("registered"));
    }

    @Test
    void testGetWorkers() throws Exception {
        registerWorker("worker-1", URI.create("http://localhost:8081"));

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + "/api/workers"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("worker-1"));
    }

    @Test
    void testHeartbeat() throws Exception {
        registerWorker("worker-1", URI.create("http://localhost:8081"));

        String requestBody = "{\"workerId\":\"worker-1\"}";
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + "/api/workers/heartbeat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("ok"));
    }

    @Test
    void testTaskSubmit() throws Exception {
        registerWorker("worker-1", URI.create("http://localhost:" + mockWorkerPort));

        Task task = createTestTask();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + "/api/tasks/submit"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(task)))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("assigned"));
    }

    @Test
    void testTaskSubmitNoWorkers() throws Exception {
        Task task = createTestTask();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + "/api/tasks/submit"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(task)))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("No available workers"));
    }

    @Test
    void testTaskResultSubmission() throws Exception {
        UUID taskId = UUID.randomUUID();
        var baos = new java.io.ByteArrayOutputStream();
        try (var oos = new java.io.ObjectOutputStream(baos)) {
            oos.writeObject("test result");
        }
        byte[] resultBytes = baos.toByteArray();
        TaskResult taskResult = TaskResult.success(taskId, resultBytes);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + "/api/tasks/result"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(taskResult)))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());

        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + "/api/tasks/" + taskId))
                .GET()
                .build();

        HttpResponse<String> getResponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getResponse.statusCode());
        assertTrue(getResponse.body().contains(taskId.toString()));
    }

    @Test
    void testGetTaskResultNotFound() throws Exception {
        UUID nonExistentTaskId = UUID.randomUUID();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + "/api/tasks/" + nonExistentTaskId))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
    }

    @Test
    void testInvalidMethod() throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + "/api/workers/register"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
    }

    private void sendResponse(com.sun.net.httpserver.HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private void registerWorker(String workerId, URI workerAddress) throws Exception {
        WorkerRegistrationRequest request = new WorkerRegistrationRequest(workerId, workerAddress);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + "/api/workers/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                .build();
        httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    }

    private Task createTestTask() {
        return new Task(
                UUID.randomUUID(),
                "TestClass",
                "testMethod",
                new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0x00, 0x00, 0x00, 0x37},
                new byte[]{},
                "test-hash",
                new TaskMetadata(Instant.now(), 1, 30000)
        );
    }

    private int findFreePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
