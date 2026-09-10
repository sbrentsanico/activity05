package edu.cit.sanico.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public InventoryItem getItem(String productId) {
        return inventoryRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
    }

    @Override
    @Transactional
    public ReserveResult reserve(String productId, int quantity) {
        InventoryItem item = inventoryRepository.findById(productId).orElse(null);
        if (item == null) {
            return new ReserveResult("REJECTED", "Product not found: " + productId, null);
        }
        if (item.getStock() < quantity) {
            return new ReserveResult("REJECTED",
                    "Insufficient stock. Requested: " + quantity + ", Available: " + item.getStock(),
                    item);
        }
        item.setStock(item.getStock() - quantity);
        inventoryRepository.save(item);
        return new ReserveResult("CONFIRMED", null, item);
    }

    @Override
    public List<InventoryItem> getAllItems() {
        return inventoryRepository.findAll();
    }
}