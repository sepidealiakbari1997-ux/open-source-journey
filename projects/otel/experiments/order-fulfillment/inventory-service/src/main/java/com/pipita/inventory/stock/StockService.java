package com.pipita.inventory.stock;

import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    private final StockRepository repository;

    public StockService(StockRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void reserve(String sku, long quantity) {
        if (quantity <= 0)
            throw new IllegalArgumentException("Quantity must be more than 0");
        Stock stock = repository.findBySkuForUpdate(sku)
                .orElseThrow(() -> new EntityNotFoundException(String.format("Stock %s not found", sku)));
        if (stock.getAvailableQuantity() < quantity)
            throw new IllegalArgumentException(String.format("Insufficient stock for SKU %s", sku));
        stock.setAvailableQuantity(stock.getAvailableQuantity() - quantity);
    }

}
