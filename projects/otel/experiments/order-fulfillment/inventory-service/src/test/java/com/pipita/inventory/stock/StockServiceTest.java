package com.pipita.inventory.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class StockServiceTest {
    @Autowired
    private StockService service;

    @Autowired
    private StockRepository repository;

    @Test
    void reservesRequestedQuantity() {

        String sku = "TEST-BOOK";

        Stock initialStock = new Stock();
        initialStock.setSku(sku);
        initialStock.setAvailableQuantity(10L);
        repository.save(initialStock);

        try {
            service.reserve(sku, 3);

            Stock finalStock = repository.findById(sku).orElseThrow();
            assertEquals(7L, finalStock.getAvailableQuantity());
        } finally {
            repository.deleteById(sku);
        }
    }

    @Test
    void rejectsReservationWhenStockIsInsufficient() {
        String sku = "TEST-BOOK";
        long availableQuantity = 10L;

        Stock initialStock = new Stock();
        initialStock.setSku(sku);
        initialStock.setAvailableQuantity(availableQuantity);
        repository.save(initialStock);

        try {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.reserve(sku, 11L));

            Stock finalStock = repository.findById(sku).orElseThrow();
            assertEquals(availableQuantity, finalStock.getAvailableQuantity());
        } finally {
            repository.deleteById(sku);
        }
    }

    @Test
    void preventsOversellingWhenReservationsCompete() {
        String sku = "TEST-BOOK";
        long availableQuantity = 11L;
        long quantity = 2L;

        Stock initialStock = new Stock();
        initialStock.setSku(sku);
        initialStock.setAvailableQuantity(availableQuantity);
        repository.save(initialStock);

        int tn = 10;
        var ready = new CountDownLatch(10);
        var start = new CountDownLatch(1);
        var successes = new AtomicInteger();
        var rejections = new AtomicInteger();

        int expectedSuccesses = 5;
        int expectedRejections = 5;
        long expectedFinalStock = 1L;

        Thread[] threads = new Thread[tn];
        Runnable task = () -> {
            ready.countDown();
            try {
                if (start.await(20, TimeUnit.SECONDS)) {
                    try {
                        service.reserve(sku, quantity);
                        successes.incrementAndGet();
                    } catch (IllegalArgumentException e) {
                        rejections.incrementAndGet();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        };

        for (int i = 0; i < tn; i++) {
            threads[i] = new Thread(task);
            threads[i].start();
        }

        try {
            ready.await(20, TimeUnit.SECONDS);
            start.countDown();
            for (int i = 0; i < tn; i++) {
                threads[i].join(20_000);
                if (threads[i].isAlive())
                    throw new AssertionError("Reservation worker did not finish in time");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Arrays.stream(threads).forEach((t) -> t.interrupt());
            throw new AssertionError("Interrupted while waiting for reservation workers", e);
        }

        try {
            Stock finalStock = repository.findById(sku).orElseThrow();
            assertEquals(expectedSuccesses, successes.get());
            assertEquals(expectedRejections, rejections.get());
            assertEquals(expectedFinalStock, finalStock.getAvailableQuantity());

        } finally {
            repository.deleteById(sku);

        }
    }

}
