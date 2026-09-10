package edu.cit.sanico.inventory;

public record ReserveResult(
        String status,
        String reason,
        InventoryItem item
) {}