package org;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadOutput_Main {
    private static final int TASKS = 12;

    public static void main(String[] args) {
        System.out.println("Starting Main");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TASKS; i++) {
                int taskNumber = i;

                executor.submit(() -> {
                    System.out.printf(
                            "Task %02d: before sleep — %s%n",
                            taskNumber,
                            Thread.currentThread()
                    );

                    try {
                        Thread.sleep(1_000);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(exception);
                    }

                    System.out.printf(
                            "Task %02d: after sleep  — %s%n",
                            taskNumber,
                            Thread.currentThread()
                    );
                });
            }
        }
    }
}