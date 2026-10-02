package org;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CpuBoundMain {
    private static final int TASKS = 320;
    private static final int LIMIT = 1_000_000;
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

     static void main(String[] args) {
        System.out.println("Starting CPU Bound Main");
        System.out.println("Avaliable processors: " + CPU_COUNT);

        runExperiment(
                "Platform threads",
                Executors.newFixedThreadPool(CPU_COUNT)
        );

        runExperiment(
                "Virtual threads",
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    private static void runExperiment(String name, ExecutorService executor) {
        Instant start = Instant.now();

        try (executor) {
            for (int i = 0; i < TASKS; i++) {
                executor.submit(() -> {
                    int primes = countPrimesUpTo(LIMIT);

                    if(primes == 0) {
                        throw new IllegalStateException("Unexpected result");
                    }
                });
            }
        }

        double seconds = Duration.between(start, Instant.now()).toMillis() / 1_000.0;

        System.out.printf("%s: %.2f seconds%n", name, seconds);
    }

    private static int countPrimesUpTo(int limit) {
        int count = 0;

        for(int number = 2; number <= limit; number++) {
            if(isPrime(number)) {
                count++;
            }
        }

        return count;
    }

    private static boolean isPrime(int number) {
        if (number < 2) {
            return false;
        }

        for(int divisor = 2; divisor * divisor <= number; divisor++) {
            if (number % divisor == 0) {
                return false;
            }
        }

        return true;
    }
}
