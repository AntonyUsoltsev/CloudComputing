package ru.nsu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.net.URI;
import java.time.Instant;

/**
 * Информация о worker-узле.
 */
@Getter
@AllArgsConstructor
public class WorkerInfo implements Serializable {
    @JsonProperty("workerId")
    private final String workerId;
    
    @JsonProperty("address")
    private final URI address; // Адрес worker-а для отправки задач
    
    @JsonProperty("activeTasks")
    private final int activeTasks; // Количество активных задач
    
    @JsonProperty("status")
    private final WorkerStatus status; // Статус worker-а
    
    @JsonProperty("lastHeartbeat")
    private final Instant lastHeartbeat; // Время последнего heartbeat

    public WorkerInfo withActiveTasks(int activeTasks) {
        return new WorkerInfo(workerId, address, activeTasks, status, lastHeartbeat);
    }

    public WorkerInfo withStatus(WorkerStatus status) {
        return new WorkerInfo(workerId, address, activeTasks, status, lastHeartbeat);
    }

    public WorkerInfo withLastHeartbeat(Instant lastHeartbeat) {
        return new WorkerInfo(workerId, address, activeTasks, status, lastHeartbeat);
    }
}

