package edu.cit.sanico.inventory.event;

public record LowStockEvent(
        String productId,
        String productName,
        int remainingStock,
        int threshold
) {}
