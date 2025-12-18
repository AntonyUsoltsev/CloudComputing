package ru.nsu;

import lombok.extern.slf4j.Slf4j;
import ru.nsu.dispatcher.DispatcherServer;

import java.io.IOException;

@Slf4j
public class Main {
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                log.error("Invalid port number, using default: " + DEFAULT_PORT, e);
            }
        }

        DispatcherServer server = new DispatcherServer(port);
        try {
            server.start();
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down...");
                server.stop();
            }));
            
            Thread.currentThread().join();
        } catch (IOException e) {
            log.error("Failed to start server", e);
        } catch (InterruptedException e) {
            log.error("Server interrupted", e);
        }
    }
}
