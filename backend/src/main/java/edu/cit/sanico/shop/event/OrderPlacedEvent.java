package edu.cit.sanico.shop.event;

import java.util.List;
import java.util.UUID;

public record OrderPlacedEvent(
        UUID orderId,
        List<ItemInfo> items
) {
    public record ItemInfo(String productId, int quantity) {}
}
