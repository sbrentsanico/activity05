package edu.cit.sanico.shop;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderHistoryResponse(
        UUID orderId,
        String status,
        String reason,
        OffsetDateTime createdAt,
        List<OrderItemDto> items
) {
    public record OrderItemDto(
            UUID itemId,
            String productId,
            int quantity
    ) {}
}
