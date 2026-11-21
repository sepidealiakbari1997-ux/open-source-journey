package com.pipita.inventory.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "STOCK")
public class Stock {

    @Id
    @Column(name = "sku", length = 100)
    private String sku;

    @Column(name = "AVAILABLE_QUANTITY", nullable = false)
    private long availableQuantity;

    public String getSku() {
        return sku;
    }

    public long getAvailableQuantity() {
        return availableQuantity;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public void setAvailableQuantity(long availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

}
