package edu.cit.sanico.shop;

import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        String status,
        String reason,
        List<ItemOutcome> items,
        List<InventoryInfo> inventory
) {
    public record ItemOutcome(String productId, String outcome) {}
    public record InventoryInfo(String productId, String name, int stock) {}
}