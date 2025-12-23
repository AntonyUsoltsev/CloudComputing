package ru.nsu.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import ru.nsu.common.JacksonConfig;
import ru.nsu.model.TaskResult;
import ru.nsu.model.WorkerRegistrationRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DispatcherClient {
    private final URI dispatcherBaseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DispatcherClient(URI dispatcherBaseUrl) {
        this.dispatcherBaseUrl = dispatcherBaseUrl;
        this.objectMapper = JacksonConfig.createObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean registerWorker(String workerId, URI workerAddress) {
        try {
            WorkerRegistrationRequest request = new WorkerRegistrationRequest(workerId, workerAddress);
            String requestBody = objectMapper.writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(dispatcherBaseUrl.resolve("/api/workers/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("Successfully registered worker {} at {}", workerId, workerAddress);
                return true;
            } else {
                log.error("Failed to register worker: {}", response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("Error registering worker", e);
            return false;
        }
    }

    public boolean sendHeartbeat(String workerId, int activeTasks) {
        try {
            Map<String, String> request = new HashMap<>();
            request.put("workerId", workerId);
            request.put("activeTasks", String.valueOf(activeTasks));
            String requestBody = objectMapper.writeValueAsString(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(dispatcherBaseUrl.resolve("/api/workers/heartbeat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("Error sending heartbeat", e);
            return false;
        }
    }

    public boolean sendTaskResult(TaskResult result) {
        try {
            String requestBody = objectMapper.writeValueAsString(result);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(dispatcherBaseUrl.resolve("/api/tasks/result"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("Error sending task result", e);
            return false;
        }
    }
}

