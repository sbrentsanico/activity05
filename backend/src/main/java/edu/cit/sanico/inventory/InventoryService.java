package edu.cit.sanico.inventory;

import java.util.List;

public interface InventoryService {
    InventoryItem getItem(String productId);
    ReserveResult reserve(String productId, int quantity);
    void restock(String productId, int quantity);
    List<InventoryItem> getAllItems();
}