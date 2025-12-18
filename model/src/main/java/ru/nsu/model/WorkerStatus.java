package ru.nsu.model;

/**
 * Статус worker-узла.
 */
public enum WorkerStatus {
    ALIVE,      // Worker активен и готов к выполнению задач
    UNAVAILABLE // Worker недоступен
}
