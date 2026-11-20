package com.ticket.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class ConcurrentRunner {

    private static final long LIMIT_SECONDS = 60;

    private ConcurrentRunner() {
    }

    static <T> List<T> runTogether(List<Callable<T>> tasks) {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
            List<Future<T>> futures = tasks.stream()
                .map(task -> executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }))
                .toList();
            awaitAllReadyThenStart(ready, start);
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(LIMIT_SECONDS, TimeUnit.SECONDS));
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the tasks", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("A task failed or did not finish", e);
        }
    }

    private static void awaitAllReadyThenStart(CountDownLatch ready, CountDownLatch start)
        throws InterruptedException {
        try {
            if (!ready.await(LIMIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The tasks did not become ready");
            }
        } finally {
            start.countDown();
        }
    }
}
