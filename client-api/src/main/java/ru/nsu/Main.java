package ru.nsu;

import lombok.extern.slf4j.Slf4j;
import ru.nsu.client.Cloud;
import ru.nsu.client.CloudClient;
import ru.nsu.client.DistributedMap;
import ru.nsu.client.RemoteFunction;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
public class Main {
    public static void main(String[] args) throws Exception {
        CloudClient client = Cloud.createClient("http://localhost:8080");

        sum_sync(client);
//        Thread.sleep(6000);
        multiply_sync(client);
//        Thread.sleep(6000);
        sym_async(client);
//        Thread.sleep(6000);
        distributedMapExample(client);

        sum_sync(client);
        sum_sync(client);
        sum_sync(client);
        sum_sync(client);

//        Thread.sleep(2000);
        longRunningTaskWithProgress(client);

    }

    private static void sum_sync(CloudClient client) throws Exception {
        log.info("sum_sync");

        RemoteFunction<Integer> sum = Cloud.remoteFunction(
                client,
                SimpleCalculator.class,
                "sum",
                Integer.class,
                Integer.class
        );
        Integer result = sum.call(32, 44);
        log.info("Result: 32 + 44 = {}", result);
    }

    private static void multiply_sync(CloudClient client) throws Exception {
        log.info("multiply_sync");

        RemoteFunction<Integer> multiply = Cloud.remoteFunction(
                client,
                SimpleCalculator.class,
                "multiply",
                Integer.class,
                Integer.class
        );
        Integer result = multiply.call(32, 44);
        log.info("Result: 32 * 44 = {}", result);
    }

    private static void sym_async(CloudClient client) throws Exception {
        log.info("sym_async");

        RemoteFunction<Integer> sum = Cloud.remoteFunction(
                client,
                SimpleCalculator.class,
                "sum",
                Integer.class,
                Integer.class
        );

        CompletableFuture<Integer> task1 = sum.callAsync(10, 20);
        CompletableFuture<Integer> task2 = sum.callAsync(30, 40);
        CompletableFuture<Integer> task3 = sum.callAsync(50, 60);

        Integer result1 = task1.get();
        Integer result2 = task2.get();
        Integer result3 = task3.get();

        log.info("Results: {}, {}, {}", result1, result2, result3);
    }

    private static void distributedMapExample(CloudClient client) throws Exception {
        log.info("distributedMapExample");

        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20);

        DistributedMap map = Cloud.distributedMap(
                client,
                SimpleCalculator.class,
                "square"
        );

        List<Integer> squared = map.map(numbers, Integer.class);
        log.info("Squared numbers: {}", squared);
    }

    private static void longRunningTaskWithProgress(CloudClient client) throws Exception {
        log.info("longRunningTaskWithProgress");

        RemoteFunction<Long> factorial = Cloud.remoteFunction(
                client,
                LongRunningTask.class,
                "factorial",
                Integer.class
        );

        Long result = factorial.call(20);
        log.info("Result: factorial(20) = {}", result);
    }
}

class SimpleCalculator {
    public static int sum(Integer a, Integer b) {
        return a + b;
    }

    public static int multiply(Integer a, Integer b) {
        return a * b;
    }

    public static List<Integer> square(List<Integer> numbers) {
        return numbers.stream()
                .map(n -> n * n)
                .collect(Collectors.toList());
    }
}

class LongRunningTask {
    public static long factorial(Integer n) {
        if (n <= 1) {
            return 1;
        }
        
        long result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        return result;
    }
}
