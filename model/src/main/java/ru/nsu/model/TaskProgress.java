package ru.nsu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Прогресс выполнения задачи
 */
@Getter
@AllArgsConstructor
public class TaskProgress implements Serializable {
    @JsonProperty("taskId")
    private final UUID taskId;

    @JsonProperty("status")
    private final TaskStatus status;

    @JsonProperty("percentage")
    private final int percentage;

    @JsonProperty("message")
    private final String message;

    public static TaskProgress create(UUID taskId, TaskStatus status, int percentage, String message) {
        return new TaskProgress(taskId, status, percentage, message);
    }

    public static TaskProgress pending(UUID taskId) {
        return create(taskId, TaskStatus.PENDING, 0, "Task pending");
    }

    public static TaskProgress running(UUID taskId, int percentage, String message) {
        return create(taskId, TaskStatus.RUNNING, percentage, message);
    }

    public static TaskProgress completed(UUID taskId) {
        return create(taskId, TaskStatus.COMPLETED, 100, "Task completed");
    }

    public static TaskProgress failed(UUID taskId, String errorMessage) {
        return create(taskId, TaskStatus.FAILED, 0, errorMessage);
    }
}

