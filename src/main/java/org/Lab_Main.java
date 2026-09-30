package org;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;

public class Lab_Main {
    private static final int NUMBER_OF_TASKS = 100_000;
    private static final int POOL_SIZE = 1_000;

    static void main() {
        System.out.println("Starting Main");
        Instant start = Instant.now();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < NUMBER_OF_TASKS; i++) {
                executor.submit(Lab_Main::fakeHttpRequest);
            }
        }

        Duration duration = Duration.between(start, Instant.now());
        System.out.println("Done in " + duration + " ms");
    }

    private static void fakeHttpRequest() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Task was interrupted", exception);
        }
    }
}