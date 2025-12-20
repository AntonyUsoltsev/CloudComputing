package ru.nsu.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Представляет задачу для выполнения на worker-узле.
 * Содержит байткод функции, сериализованные аргументы и метаданные.
 */
@Getter
@AllArgsConstructor
public class Task implements Serializable {
    @JsonProperty("taskId")
    private final UUID taskId;
    
    @JsonProperty("className")
    private final String className;
    
    @JsonProperty("methodName")
    private final String methodName;
    
    @JsonProperty("classBytes")
    private final byte[] classBytes; // Байткод класса
    
    @JsonProperty("arguments")
    private final byte[] arguments; // Сериализованные аргументы
    
    @JsonProperty("codeHash")
    private final String codeHash; // Хэш байткода для кэширования
    
    @JsonProperty("metadata")
    private final TaskMetadata metadata;
}
