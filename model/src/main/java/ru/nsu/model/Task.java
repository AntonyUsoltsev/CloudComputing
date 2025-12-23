package ru.nsu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.util.UUID;

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
    private final byte[] classBytes;
    
    @JsonProperty("arguments")
    private final byte[] arguments;
    
    @JsonProperty("codeHash")
    private final String codeHash;
    
    @JsonProperty("metadata")
    private final TaskMetadata metadata;
}
