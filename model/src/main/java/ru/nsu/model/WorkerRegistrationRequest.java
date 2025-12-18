package ru.nsu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.net.URI;

/**
 * Запрос на регистрацию worker-а.
 */
@Getter
@AllArgsConstructor
public class WorkerRegistrationRequest implements Serializable {
    @JsonProperty("workerId")
    private final String workerId;
    
    @JsonProperty("address")
    private final URI address;
}

