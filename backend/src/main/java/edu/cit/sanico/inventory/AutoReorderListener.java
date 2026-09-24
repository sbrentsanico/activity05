package edu.cit.sanico.inventory;

import edu.cit.sanico.inventory.event.LowStockEvent;
import edu.cit.sanico.supplier.SupplierGateway;
import edu.cit.sanico.supplier.SupplierOrderResult;
import edu.cit.sanico.supplier.SupplierOrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
class AutoReorderListener {

    private static final Logger log = LoggerFactory.getLogger(AutoReorderListener.class);

    private final SupplierGateway supplierGateway;
    private final InventoryService inventoryService;
    private final Set<String> activeReorders = ConcurrentHashMap.newKeySet();

    AutoReorderListener(SupplierGateway supplierGateway, InventoryService inventoryService) {
        this.supplierGateway = supplierGateway;
        this.inventoryService = inventoryService;
    }

    @EventListener
    @Async
    public void onLowStock(LowStockEvent event) {
        String productId = event.productId();
        if (!activeReorders.add(productId)) {
            log.info("[AutoReorder] Reorder already in progress for product {}", productId);
            return;
        }

        try {
            int unitsNeeded = Math.max(10, (event.threshold() * 2) - event.remainingStock());
            String buyerRef = "REORDER-" + productId + "-" + System.currentTimeMillis();

            log.info("[AutoReorder] Low-stock triggered for product {} (stock={}, threshold={}). Requesting {} units.",
                productId, event.remainingStock(), event.threshold(), unitsNeeded);

            SupplierOrderResult orderResult = supplierGateway.placeReorder(productId, unitsNeeded, buyerRef);
            log.info("[AutoReorder] Supplier order {} confirmed. Status: {}, Ordered units: {}",
                orderResult.supplierOrderId(), orderResult.status(), orderResult.unitsOrdered());

            // Track order status politely until delivered
            SupplierOrderResult terminalResult = supplierGateway.trackOrderToDelivery(orderResult.supplierOrderId());

            if (terminalResult.status() == SupplierOrderStatus.DELIVERED) {
                log.info("[AutoReorder] PO {} delivered! Restocking {} units for product {}",
                    terminalResult.supplierOrderId(), terminalResult.unitsOrdered(), productId);
                inventoryService.restock(productId, terminalResult.unitsOrdered());
            } else {
                log.warn("[AutoReorder] PO {} ended with status {}", terminalResult.supplierOrderId(), terminalResult.status());
            }
        } catch (Exception e) {
            log.error("[AutoReorder] Error processing auto-reorder for product {}: {}", productId, e.getMessage(), e);
        } finally {
            activeReorders.remove(productId);
        }
    }
}
