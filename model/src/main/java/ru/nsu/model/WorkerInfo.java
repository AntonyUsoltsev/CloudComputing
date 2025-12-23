package ru.nsu.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Информация о worker-узле.
 */
@Getter
@AllArgsConstructor
public class WorkerInfo implements Serializable {
    @JsonProperty("workerId")
    private final String workerId;

    @JsonProperty("address")
    private final URI address;

    @JsonIgnore
    private final Set<UUID> activeTaskIds;

    @JsonProperty("status")
    private final WorkerStatus status;

    @JsonProperty("lastHeartbeat")
    private final Instant lastHeartbeat;

    public WorkerInfo(String workerId, URI address, WorkerStatus status, Instant lastHeartbeat) {
        this.workerId = workerId;
        this.address = address;
        this.activeTaskIds = ConcurrentHashMap.newKeySet();
        this.status = status;
        this.lastHeartbeat = lastHeartbeat;
    }

    /**
     * Возвращает количество активных задач.
     */
    @JsonProperty("activeTasks")
    public int getActiveTasks() {
        return activeTaskIds.size();
    }

    public WorkerInfo withStatus(WorkerStatus status) {
        return new WorkerInfo(workerId, address, activeTaskIds, status, lastHeartbeat);
    }

    public WorkerInfo withLastHeartbeat(Instant lastHeartbeat) {
        return new WorkerInfo(workerId, address, activeTaskIds, status, lastHeartbeat);
    }

    /**
     * Добавляет задачу в Set активных задач.
     */
    public void addTask(UUID taskId) {
        activeTaskIds.add(taskId);
    }

    /**
     * Удаляет задачу из Set активных задач.
     */
    public void removeTask(UUID taskId) {
        activeTaskIds.remove(taskId);
    }
}

