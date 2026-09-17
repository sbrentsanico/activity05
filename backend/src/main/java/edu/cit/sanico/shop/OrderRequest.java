package edu.cit.sanico.shop;

import java.util.List;

public record OrderRequest(List<LineItemRequest> items) {
    public record LineItemRequest(String productId, int quantity) {}
}