package org;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlockingHttp_Main {
    private static final int NUMBER_OF_TASKS = 20;

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static final HttpRequest REQUEST = HttpRequest.newBuilder()
            .uri(URI.create("https://httpbin.org/delay/1"))
            .GET()
            .build();

    public static void main(String[] args) {
        Instant start = Instant.now();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < NUMBER_OF_TASKS; i++) {
                int taskNumber = i;
                executor.submit(() -> sendRequest(taskNumber));
            }
        }

        System.out.printf(
                "All requests finished in %.2f seconds%n",
                Duration.between(start, Instant.now()).toMillis() / 1_000.0
        );
    }

    private static void sendRequest(int taskNumber) {
        try {
            HttpResponse<Void> response = HTTP_CLIENT.send(
                    REQUEST,
                    HttpResponse.BodyHandlers.discarding()
            );

            System.out.printf(
                    "Task %02d: HTTP %d%n",
                    taskNumber,
                    response.statusCode()
            );
        } catch (Exception exception) {
            System.err.printf(
                    "Task %02d failed: %s%n",
                    taskNumber,
                    exception.getMessage()
            );
        }
    }
}