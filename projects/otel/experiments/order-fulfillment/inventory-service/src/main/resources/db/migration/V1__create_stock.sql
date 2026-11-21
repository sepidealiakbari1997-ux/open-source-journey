CREATE TABLE stock (
    sku VARCHAR(100) PRIMARY KEY,
    available_quantity BIGINT NOT NULL,
    CONSTRAINT stock_available_quantity_nonnegative CHECK (available_quantity >= 0)
);
