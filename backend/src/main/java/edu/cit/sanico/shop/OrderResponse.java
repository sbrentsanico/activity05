package edu.cit.sanico.shop;

public record OrderResponse(
        String status,
        String reason,
        InventoryInfo inventory
) {
    public record InventoryInfo(String productId, String name, int stock) {}
}