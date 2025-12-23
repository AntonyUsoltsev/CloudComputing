package ru.nsu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Результат выполнения задачи.
 */
@Getter
@AllArgsConstructor
public class TaskResult implements Serializable {
    @JsonProperty("taskId")
    private final UUID taskId;
    
    @JsonProperty("success")
    private final boolean success;
    
    @JsonProperty("result")
    private final byte[] result;
    
    @JsonProperty("errorMessage")
    private final String errorMessage;

    public static TaskResult success(UUID taskId, byte[] result) {
        return new TaskResult(taskId, true, result, null);
    }

    public static TaskResult failure(UUID taskId, String errorMessage) {
        return new TaskResult(taskId, false, null, errorMessage);
    }
}

