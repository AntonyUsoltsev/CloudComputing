package ru.nsu.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import ru.nsu.common.JacksonConfig;
import ru.nsu.model.Task;
import ru.nsu.model.TaskResult;
import ru.nsu.model.WorkerInfo;
import ru.nsu.model.WorkerRegistrationRequest;
import ru.nsu.model.WorkerStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
    private final Map<UUID, String> taskToWorker; // Маппинг taskId -> workerId
    private final HttpClient httpClient;
    private HttpServer httpServer;

    public DispatcherServer(int port) {
        this.port = port;
        this.objectMapper = JacksonConfig.createObjectMapper();
        this.workers = new ConcurrentHashMap<>();
        this.taskToWorker = new ConcurrentHashMap<>();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
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

            WorkerInfo worker = workers.get(workerId);
            if (worker != null) {
                workers.put(workerId, worker
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

            // Выбираем worker для выполнения задачи
            WorkerInfo selectedWorker = selectWorker();
            if (selectedWorker == null) {
                sendError(exchange, 503, "No available workers");
                return;
            }

            log.info("Task {} assigned to worker {}", task.getTaskId(), selectedWorker.getWorkerId());

            // Отправляем задачу worker-у напрямую
            boolean sent = sendTaskToWorker(task, selectedWorker);
            if (!sent) {
                sendError(exchange, 500, "Failed to send task to worker");
                return;
            }

            selectedWorker.addTask(task.getTaskId());
            taskToWorker.put(task.getTaskId(), selectedWorker.getWorkerId());

            Map<String, String> response = new HashMap<>();
            response.put("taskId", task.getTaskId().toString());
            response.put("workerId", selectedWorker.getWorkerId());
            response.put("status", "assigned");

            sendSuccessResponse(exchange, objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            log.error("Error submitting task", e);
            sendError(exchange, 400, "Invalid request: " + e.getMessage());
        }
    }

    /**
     * Отправляет задачу worker-у напрямую через HTTP POST.
     */
    private boolean sendTaskToWorker(Task task, WorkerInfo worker) {
        try {
            String taskJson = objectMapper.writeValueAsString(task);
            URI workerTaskUrl = worker.getAddress().resolve("/api/tasks/execute");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(workerTaskUrl)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(taskJson))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.debug("Task {} sent to worker {} successfully", task.getTaskId(), worker.getWorkerId());
                return true;
            } else {
                log.warn("Failed to send task {} to worker {}: status {}",
                        task.getTaskId(), worker.getWorkerId(), response.statusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("Error sending task {} to worker {}", task.getTaskId(), worker.getWorkerId(), e);
            return false;
        }
    }

    private void handleTaskResult(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            TaskResult result = objectMapper.readValue(exchange.getRequestBody(), TaskResult.class);

            String workerId = taskToWorker.remove(result.getTaskId());
            if (workerId != null) {
                WorkerInfo worker = workers.get(workerId);
                if (worker != null) {
                    worker.removeTask(result.getTaskId());
                }
            }

            if (result.isSuccess()) {
                Object deserializedResult;
                try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(result.getResult()))) {
                    deserializedResult = ois.readObject();
                }

                log.info("Task {} completed successfully. Result: {}",
                        result.getTaskId(), deserializedResult);
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

