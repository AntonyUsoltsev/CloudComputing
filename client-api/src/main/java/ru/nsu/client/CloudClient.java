package ru.nsu.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import ru.nsu.common.JacksonConfig;
import ru.nsu.model.Task;
import ru.nsu.model.TaskProgress;
import ru.nsu.model.TaskResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Клиент для взаимодействия с Dispatcher
 * Обеспечивает отправку задач, получение результатов и управление выполнением
 */
@Slf4j
public class CloudClient {
    private final URI dispatcherUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CloudClient(URI dispatcherUrl) {
        this.dispatcherUrl = dispatcherUrl;
        this.objectMapper = JacksonConfig.createObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public <T> T executeTask(Task task, Class<T> resultType) throws IOException, ClassNotFoundException, InterruptedException {
        submitTask(task);

        TaskResult result = pollTaskResult(task.getTaskId(), 30000);

        if (!result.isSuccess()) {
            throw new RuntimeException("Task execution failed: " + result.getErrorMessage());
        }

        return deserializeResult(result.getResult(), resultType);
    }

    public <T> CompletableFuture<T> executeTaskAsync(Task task, Class<T> resultType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                submitTask(task);
                TaskResult result = pollTaskResult(task.getTaskId(), 30000);

                if (!result.isSuccess()) {
                    throw new RuntimeException("Task execution failed: " + result.getErrorMessage());
                }

                return deserializeResult(result.getResult(), resultType);
            } catch (Exception e) {
                throw new RuntimeException("Failed to execute task", e);
            }
        });
    }

    public void submitTask(Task task) throws IOException, InterruptedException {
        String taskJson = objectMapper.writeValueAsString(task);
        URI submitUrl = dispatcherUrl.resolve("/api/tasks/submit");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(submitUrl)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(taskJson))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to submit task: HTTP " + response.statusCode() + " - " + response.body());
        }

        log.info("Task {} submitted successfully", task.getTaskId());
    }

    public TaskResult pollTaskResult(UUID taskId, long timeoutMs) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        long pollInterval = 1000;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            TaskResult result = getTaskResult(taskId);
            if (result != null) {
                return result;
            }
            
            TaskProgress progress = getTaskProgress(taskId);
            if (progress != null) {
                log.info("Task {} progress: {}% - {} (Status: {})", 
                        taskId, progress.getPercentage(), progress.getMessage(), progress.getStatus());
            }
            
            Thread.sleep(pollInterval);
        }

        throw new IOException("Task result timeout: task " + taskId + " did not complete in " + timeoutMs + "ms");
    }

    public TaskProgress getTaskProgress(UUID taskId) throws IOException, InterruptedException {
        URI progressUrl = dispatcherUrl.resolve("/api/tasks/" + taskId + "?progress");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(progressUrl)
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            return null;
        }

        if (response.statusCode() != 200) {
            throw new IOException("Failed to get task progress: HTTP " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), TaskProgress.class);
    }

    public TaskResult getTaskResult(UUID taskId) throws IOException, InterruptedException {
        URI resultUrl = dispatcherUrl.resolve("/api/tasks/" + taskId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(resultUrl)
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            return null;
        }

        if (response.statusCode() != 200) {
            throw new IOException("Failed to get task result: HTTP " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), TaskResult.class);
    }

    public static byte[] serializeArguments(Object... args) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(args);
            return baos.toByteArray();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T deserializeResult(byte[] data, Class<T> resultType) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            Object result = ois.readObject();
            return (T) result;
        }
    }
}

