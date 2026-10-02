package edu.cit.sanico.inventory.event;

public record InventoryUpdatedEvent(
        String productId,
        String name,
        int newStock
) {}
