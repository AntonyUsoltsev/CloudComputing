package ru.nsu.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.nsu.common.JacksonConfig;
import ru.nsu.model.TaskResult;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributedMapTest {
    private HttpServer testServer;
    private CloudClient client;
    private DistributedMap distributedMap;
    private int serverPort;
    private final ObjectMapper objectMapper = JacksonConfig.createObjectMapper();

    @BeforeEach
    void setUp() throws IOException {

        serverPort = findFreePort();
        testServer = HttpServer.create(new InetSocketAddress("localhost", serverPort), 0);

        testServer.createContext("/api/tasks/submit", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
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
                    UUID taskId = UUID.fromString(parts[3]);

                    try {
                        List<Integer> output = Arrays.asList(2, 4, 6);

                        var baos = new java.io.ByteArrayOutputStream();
                        try (var oos = new java.io.ObjectOutputStream(baos)) {
                            oos.writeObject(output);
                        }
                        byte[] resultBytes = baos.toByteArray();

                        TaskResult result = TaskResult.success(taskId, resultBytes);
                        String json = objectMapper.writeValueAsString(result);
                        sendResponse(exchange, 200, json);
                    } catch (Exception e) {
                        sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
                    }
                } else {
                    sendResponse(exchange, 404, "{\"error\":\"Not found\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            }
        });

        testServer.start();
        client = new CloudClient(URI.create("http://localhost:" + serverPort));

        byte[] mapperClassBytes = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0x00, 0x00, 0x00, 0x37};
        distributedMap = new DistributedMap(client, "MapperClass", "map", mapperClassBytes, 2);
    }

    @AfterEach
    void tearDown() {
        if (testServer != null) {
            testServer.stop(0);
        }
    }

    @Test
    void testMap() throws Exception {
        List<Integer> input = Arrays.asList(1, 2, 3, 4, 5);

        List<Integer> result = distributedMap.map(input, Integer.class);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(List.of(2, 4, 6,
                        2, 4, 6,
                        2, 4, 6),
                result
        );
    }

    @Test
    void testMapAsync() throws Exception {
        List<Integer> input = Arrays.asList(1, 2, 3, 4);

        CompletableFuture<List<Integer>> future = distributedMap.mapAsync(input, Integer.class);
        List<Integer> result = future.get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals(List.of(2, 4, 6,
                        2, 4, 6),
                result
        );
    }

    @Test
    void testMapWithEmptyList() throws Exception {
        List<Integer> input = new ArrayList<>();

        List<Integer> result = distributedMap.map(input, Integer.class);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testMapWithSingleChunk() throws Exception {
        List<Integer> input = Arrays.asList(1, 2);

        DistributedMap largeChunkMap = new DistributedMap(client, "MapperClass", "map",
                new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}, 10);

        List<Integer> result = largeChunkMap.map(input, Integer.class);

        assertNotNull(result);
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
