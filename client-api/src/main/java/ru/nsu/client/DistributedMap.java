package ru.nsu.client;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.nsu.model.Task;
import ru.nsu.model.TaskMetadata;
import ru.nsu.utils.HashUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Реализация распределенного parallel map
 * Разбивает входную коллекцию на чанки и распределяет их между worker-узлами
 */
@Slf4j
@AllArgsConstructor
public class DistributedMap {
    private final CloudClient client;
    private final String mapperClassName;
    private final String mapperMethodName;
    private final byte[] mapperClassBytes;
    private final int chunkSize;

    public <T, R> List<R> map(List<T> input, Class<R> resultType) throws Exception {
        List<List<T>> chunks = partition(input, chunkSize);
        log.info("Split input into {} chunks", chunks.size());

        List<CompletableFuture<List<R>>> futures = new ArrayList<>();
        for (List<T> chunk : chunks) {
            try {
                Task task = createMapTask(chunk);
                CompletableFuture<List<R>> future = client.executeTaskAsync(task, List.class)
                        .thenApply(result -> {
                            @SuppressWarnings("unchecked")
                            List<R> typedResult = (List<R>) result;
                            return typedResult;
                        });
                futures.add(future);
            } catch (IOException e) {
                CompletableFuture<List<R>> failed = new CompletableFuture<>();
                failed.completeExceptionally(e);
                futures.add(failed);
            }
        }

        List<R> results = new ArrayList<>();
        for (CompletableFuture<List<R>> future : futures) {
            try {
                results.addAll(future.get());
            } catch (ExecutionException e) {
                throw new RuntimeException("Failed to execute chunk", e);
            }
        }

        return results;
    }

    public <T, R> CompletableFuture<List<R>> mapAsync(List<T> input, Class<R> resultType) {
        List<List<T>> chunks = partition(input, chunkSize);
        log.info("Split input into {} chunks", chunks.size());

        List<CompletableFuture<List<R>>> futures = chunks.stream()
                .map(chunk -> {
                    try {
                        Task task = createMapTask(chunk);
                        return client.executeTaskAsync(task, List.class)
                                .thenApply(result -> {
                                    @SuppressWarnings("unchecked")
                                    List<R> typedResult = (List<R>) result;
                                    return typedResult;
                                });
                    } catch (Exception e) {
                        CompletableFuture<List<R>> failed = new CompletableFuture<>();
                        failed.completeExceptionally(e);
                        return failed;
                    }
                })
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<R> results = new ArrayList<>();
                    for (CompletableFuture<List<R>> future : futures) {
                        try {
                            results.addAll(future.get());
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to execute chunk", e);
                        }
                    }
                    return results;
                });
    }

    private <T> Task createMapTask(List<T> chunk) throws IOException {
        byte[] arguments = CloudClient.serializeArguments(chunk);

        String codeHash = HashUtils.calculateHash(mapperClassBytes);

        TaskMetadata metadata = new TaskMetadata(Instant.now(), 1, 60000);

        return new Task(
                UUID.randomUUID(),
                mapperClassName,
                mapperMethodName,
                mapperClassBytes,
                arguments,
                codeHash,
                metadata
        );
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            int end = Math.min(i + size, list.size());
            partitions.add(new ArrayList<>(list.subList(i, end)));
        }
        return partitions;
    }
}

