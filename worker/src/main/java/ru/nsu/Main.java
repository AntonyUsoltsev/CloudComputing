package ru.nsu;

import lombok.extern.slf4j.Slf4j;
import ru.nsu.worker.WorkerServer;

import java.io.IOException;
import java.net.URI;

@Slf4j
public class Main {
    private static final int DEFAULT_WORKER_PORT = 8081;
    private static final String DEFAULT_DISPATCHER_URL = "http://localhost:8080";
    private static final String DEFAULT_WORKER_ID = "worker-" + System.currentTimeMillis();

    public static void main(String[] args) {
        int workerPort = DEFAULT_WORKER_PORT;
        String dispatcherUrl = DEFAULT_DISPATCHER_URL;
        String workerId = DEFAULT_WORKER_ID;

        if (args.length > 0) {
            try {
                workerPort = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                log.error("Invalid worker port number, using default: {}", DEFAULT_WORKER_PORT);
            }
        }
        if (args.length > 1) {
            dispatcherUrl = args[1];
        }
        if (args.length > 2) {
            workerId = args[2];
        }

        WorkerServer server = new WorkerServer(workerId, workerPort, URI.create(dispatcherUrl));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            server.stop();
        }));

        try {
            server.start();
            Thread.currentThread().join();
        } catch (IOException e) {
            log.error("Failed to start worker server", e);
        } catch (InterruptedException e) {
            log.error("Worker interrupted", e);
        }
    }
}
