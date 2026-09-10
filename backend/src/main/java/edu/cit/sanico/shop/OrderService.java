package edu.cit.sanico.shop;

public interface OrderService {
    OrderResponse placeOrder(String productId, int quantity);
}