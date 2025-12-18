package ru.nsu.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import ru.nsu.model.Task;
import ru.nsu.model.TaskResult;
import ru.nsu.model.WorkerInfo;
import ru.nsu.model.WorkerRegistrationRequest;
import ru.nsu.model.WorkerStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Центральный REST сервис для управления распределённым выполнением задач.
 * Обеспечивает регистрацию worker-ов, приём задач и балансировку нагрузки.
 */
@Slf4j
public class DispatcherServer {
    private final int port;
    private final ObjectMapper objectMapper;
    private final Map<String, WorkerInfo> workers;
    private final Map<UUID, Task> pendingTasks;
    private HttpServer httpServer;

    public DispatcherServer(int port) {
        this.port = port;
        this.objectMapper = new ObjectMapper();
        this.workers = new ConcurrentHashMap<>();
        this.pendingTasks = new ConcurrentHashMap<>();
    }

    /**
     * Запускает сервер.
     */
    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Регистрация worker-а
        httpServer.createContext("/api/workers/register", this::handleWorkerRegistration);
        
        // Heartbeat от worker-а
        httpServer.createContext("/api/workers/heartbeat", this::handleHeartbeat);
        
        // Получение списка worker-ов
        httpServer.createContext("/api/workers", this::handleGetWorkers);
        
        // Отправка задачи на выполнение
        httpServer.createContext("/api/tasks/submit", this::handleTaskSubmit);
        
        // Получение задачи для выполнения (для worker-а)
        httpServer.createContext("/api/tasks/get", this::handleGetTask);
        
        // Отправка результата выполнения (от worker-а)
        httpServer.createContext("/api/tasks/result", this::handleTaskResult);

        httpServer.setExecutor(null); // Используем дефолтный executor
        httpServer.start();
        log.info("Dispatcher server started on port {}", port);
    }

    /**
     * Останавливает сервер.
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            log.info("Dispatcher server stopped");
        }
    }

    private void handleWorkerRegistration(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            WorkerRegistrationRequest request = objectMapper.readValue(
                    exchange.getRequestBody(), WorkerRegistrationRequest.class);

            WorkerInfo workerInfo = new WorkerInfo(
                    request.getWorkerId(),
                    request.getAddress(),
                    0,
                    WorkerStatus.ALIVE,
                    Instant.now()
            );

            workers.put(request.getWorkerId(), workerInfo);
            log.info("Worker registered: {} at {}", request.getWorkerId(), request.getAddress());

            sendSuccessResponse(exchange, "{\"status\":\"registered\"}");
        } catch (Exception e) {
            log.error("Error registering worker", e);
            sendError(exchange, 400, "Invalid request: " + e.getMessage());
        }
    }

    private void handleHeartbeat(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            Map<String, String> request = objectMapper.readValue(
                    exchange.getRequestBody(), Map.class);
            String workerId = request.get("workerId");
            int activeTasks = Integer.parseInt(request.getOrDefault("activeTasks", "0"));

            WorkerInfo worker = workers.get(workerId);
            if (worker != null) {
                workers.put(workerId, worker
                        .withActiveTasks(activeTasks)
                        .withLastHeartbeat(Instant.now())
                        .withStatus(WorkerStatus.ALIVE));
            }

            sendSuccessResponse(exchange, "{\"status\":\"ok\"}");
        } catch (Exception e) {
            log.error("Error processing heartbeat", e);
            sendError(exchange, 400, "Invalid request: " + e.getMessage());
        }
    }

    private void handleGetWorkers(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        List<WorkerInfo> workerList = new ArrayList<>(workers.values());
        String response = objectMapper.writeValueAsString(workerList);
        sendSuccessResponse(exchange, response);
    }

    private void handleTaskSubmit(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            Task task = objectMapper.readValue(exchange.getRequestBody(), Task.class);
            pendingTasks.put(task.getTaskId(), task);

            // Выбираем worker для выполнения задачи
            WorkerInfo selectedWorker = selectWorker();
            if (selectedWorker == null) {
                sendError(exchange, 503, "No available workers");
                return;
            }

            log.info("Task {} assigned to worker {}", task.getTaskId(), selectedWorker.getWorkerId());

            // MVP
            Map<String, String> response = new HashMap<>();
            response.put("taskId", task.getTaskId().toString());
            response.put("workerId", selectedWorker.getWorkerId());
            response.put("workerAddress", selectedWorker.getAddress().toString());

            sendSuccessResponse(exchange, objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            log.error("Error submitting task", e);
            sendError(exchange, 400, "Invalid request: " + e.getMessage());
        }
    }

    private void handleGetTask(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        // В MVP возвращаем первую доступную задачу
        if (pendingTasks.isEmpty()) {
            sendSuccessResponse(exchange, "");
            return;
        }

        Task task = pendingTasks.values().iterator().next();
        pendingTasks.remove(task.getTaskId());

        String response = objectMapper.writeValueAsString(task);
        sendSuccessResponse(exchange, response);
    }

    private void handleTaskResult(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            TaskResult result = objectMapper.readValue(exchange.getRequestBody(), TaskResult.class);
            if (result.isSuccess()) {
                log.info("Task {} completed successfully", result.getTaskId());
            } else {
                log.warn("Task {} failed: {}", result.getTaskId(), result.getErrorMessage());
            }

            sendSuccessResponse(exchange, "{\"status\":\"received\"}");
        } catch (Exception e) {
            log.error("Error processing task result", e);
            sendError(exchange, 400, "Invalid request: " + e.getMessage());
        }
    }

    /**
     * Выбирает worker для выполнения задачи по принципу минимальной загрузки.
     */
    private WorkerInfo selectWorker() {
        return workers.values().stream()
                .filter(w -> w.getStatus() == WorkerStatus.ALIVE)
                .min(Comparator.comparingInt(WorkerInfo::getActiveTasks))
                .orElse(null);
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

