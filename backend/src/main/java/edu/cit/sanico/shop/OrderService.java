package edu.cit.sanico.shop;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    OrderResponse placeOrder(OrderRequest request);
    OrderResponse cancelOrder(UUID orderId);
    List<OrderHistoryResponse> getOrderHistory();
}