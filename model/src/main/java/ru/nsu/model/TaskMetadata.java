package ru.nsu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;

/**
 * Метаданные задачи.
 */
@Getter
@AllArgsConstructor
public class TaskMetadata implements Serializable {
    @JsonProperty("createdAt")
    private final Instant createdAt;
    
    @JsonProperty("priority")
    private final int priority;
    
    @JsonProperty("timeoutMs")
    private final long timeoutMs;
}

