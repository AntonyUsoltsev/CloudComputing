package ru.nsu.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import ru.nsu.common.JacksonConfig;
import ru.nsu.model.Task;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Основной сервер worker-узла.
 * Управляет регистрацией, heartbeat, получением и выполнением задач.
 */
@Slf4j
public class WorkerServer {
    private final String workerId;
    private final int workerPort;
    private final DispatcherClient dispatcherClient;
    private final DynamicClassLoader classLoader;
    private final TaskExecutor taskExecutor;
    private final ScheduledExecutorService scheduler;
    private final ObjectMapper objectMapper;
    private HttpServer httpServer;
    private volatile boolean running = false;

    public WorkerServer(String workerId, int workerPort, URI dispatcherUrl) {
        this.workerId = workerId;
        this.workerPort = workerPort;
        this.dispatcherClient = new DispatcherClient(dispatcherUrl);
        this.classLoader = new DynamicClassLoader(Thread.currentThread().getContextClassLoader());
        int threadPoolSize = Runtime.getRuntime().availableProcessors();
        this.taskExecutor = new TaskExecutor(classLoader, threadPoolSize);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.objectMapper = JacksonConfig.createObjectMapper();
    }

    public void start() throws IOException {
        if (running) {
            log.warn("Worker server is already running");
            return;
        }

        running = true;
        log.info("Starting worker server: {} on port {}", workerId, workerPort);

        httpServer = HttpServer.create(new InetSocketAddress(workerPort), 0);
        httpServer.createContext("/api/tasks/execute", this::handleTaskExecution);
        httpServer.setExecutor(null);
        httpServer.start();
        log.info("Worker HTTP server started on port {}", workerPort);

        URI workerAddress = URI.create("http://localhost:" + workerPort);
        if (!dispatcherClient.registerWorker(workerId, workerAddress)) {
            log.error("Failed to register worker, stopping");
            stop();
            return;
        }

        scheduler.scheduleAtFixedRate(
                this::sendHeartbeat,
                5, // Начальная задержка 5 секунд
                10, // Период 10 секунд
                TimeUnit.SECONDS
        );

        log.info("Worker server started successfully");
    }

    public void stop() {
        if (!running) {
            return;
        }

        log.info("Stopping worker server");
        running = false;

        if (httpServer != null) {
            httpServer.stop(0);
        }

        scheduler.shutdown();
        taskExecutor.shutdown();
        log.info("Worker server stopped");
    }

    private void sendHeartbeat() {
        if (!running) {
            return;
        }

        int activeTasks = taskExecutor.getActiveTasks();
        if (!dispatcherClient.sendHeartbeat(workerId, activeTasks)) {
            log.warn("Failed to send heartbeat");
        }
    }

    private void handleTaskExecution(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }


        try {
            Task task = objectMapper.readValue(exchange.getRequestBody(), Task.class);
            log.info("Received task {} from dispatcher", task.getTaskId());
            sendSuccessResponse(exchange, "{\"status\":\"accepted\"}");
            taskExecutor.executeTaskAsync(task, result -> {
                if (!dispatcherClient.sendTaskResult(result)) {
                    log.error("Failed to send task result for task {}", task.getTaskId());
                }
            });
        } catch (Exception e) {
            log.error("Error processing task execution request", e);
            sendError(exchange, 400, "Invalid request: " + e.getMessage());
        }
    }

    private void sendSuccessResponse(HttpExchange exchange, String response) throws IOException {
        sendResponse(exchange, 200, response);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        String errorResponse = "{\"error\":\"" + message + "\"}";
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, errorResponse.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(errorResponse.getBytes());
        }
    }
}
