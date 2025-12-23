package ru.nsu.worker;

import lombok.extern.slf4j.Slf4j;
import ru.nsu.model.Task;
import ru.nsu.model.TaskResult;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Основной сервер worker-узла.
 * Управляет регистрацией, heartbeat, получением и выполнением задач.
 */
@Slf4j
public class WorkerServer {
    private final String workerId;
    private final int workerPort;
    private final URI dispatcherUrl;
    private final DispatcherClient dispatcherClient;
    private final DynamicClassLoader classLoader;
    private final TaskExecutor taskExecutor;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    public WorkerServer(String workerId, int workerPort, URI dispatcherUrl) {
        this.workerId = workerId;
        this.workerPort = workerPort;
        this.dispatcherUrl = dispatcherUrl;
        this.dispatcherClient = new DispatcherClient(dispatcherUrl);
        this.classLoader = new DynamicClassLoader(Thread.currentThread().getContextClassLoader());
        int threadPoolSize = Runtime.getRuntime().availableProcessors();
        this.taskExecutor = new TaskExecutor(classLoader, threadPoolSize);
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    public void start() {
        if (running) {
            log.warn("Worker server is already running");
            return;
        }

        running = true;
        log.info("Starting worker server: {} on port {}", workerId, workerPort);

        URI workerAddress = URI.create("http://localhost:" + workerPort);
        if (!dispatcherClient.registerWorker(workerId, workerAddress)) {
            log.error("Failed to register worker, stopping");
            return;
        }

        scheduler.scheduleAtFixedRate(
                this::sendHeartbeat,
                5, // Начальная задержка 5 секунд
                10, // Период 10 секунд
                TimeUnit.SECONDS
        );

        scheduler.submit(this::taskPollingLoop);

        log.info("Worker server started successfully");
    }

    public void stop() {
        if (!running) {
            return;
        }

        log.info("Stopping worker server");
        running = false;
        scheduler.shutdown();
        taskExecutor.shutdown();
        log.info("Worker server stopped");
    }

    private void sendHeartbeat() {
        if (!running) {
            return;
        }

        int activeTasks = taskExecutor.getActiveTasks();
        if (!dispatcherClient.sendHeartbeat(workerId, activeTasks)) {
            log.warn("Failed to send heartbeat");
        }
    }

    private void taskPollingLoop() {
        log.info("Starting task polling loop");
        
        while (running) {
            try {
                Task task = dispatcherClient.getTask();
                
                if (task != null) {
                    log.info("Received task {}", task.getTaskId());
                    
                    taskExecutor.executeTaskAsync(task, result -> {
                        if (!dispatcherClient.sendTaskResult(result)) {
                            log.error("Failed to send task result for task {}", task.getTaskId());
                        }
                    });
                } else {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Task polling loop interrupted");
                break;
            } catch (Exception e) {
                log.error("Error in task polling loop", e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        log.info("Task polling loop stopped");
    }
}

