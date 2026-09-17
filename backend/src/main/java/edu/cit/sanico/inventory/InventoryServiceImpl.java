package edu.cit.sanico.inventory;

import edu.cit.sanico.inventory.event.LowStockEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final int lowStockThreshold;

    InventoryServiceImpl(
            InventoryRepository inventoryRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${inventory.low-stock-threshold:5}") int lowStockThreshold
    ) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.lowStockThreshold = lowStockThreshold;
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

        if (item.getStock() < lowStockThreshold) {
            eventPublisher.publishEvent(new LowStockEvent(
                    item.getProductId(),
                    item.getName(),
                    item.getStock(),
                    lowStockThreshold
            ));
        }

        return new ReserveResult("CONFIRMED", null, item);
    }

    @Override
    @Transactional
    public void restock(String productId, int quantity) {
        InventoryItem item = inventoryRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        item.setStock(item.getStock() + quantity);
        inventoryRepository.save(item);
    }

    @Override
    public List<InventoryItem> getAllItems() {
        return inventoryRepository.findAll();
    }
}