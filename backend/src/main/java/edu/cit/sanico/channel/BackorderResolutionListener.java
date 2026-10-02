package edu.cit.sanico.channel;

import edu.cit.sanico.inventory.event.InventoryUpdatedEvent;
import edu.cit.sanico.shop.OrderRequest;
import edu.cit.sanico.shop.OrderResponse;
import edu.cit.sanico.shop.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
class BackorderResolutionListener {

    private static final Logger log = LoggerFactory.getLogger(BackorderResolutionListener.class);

    private final BackorderTracker backorderTracker;
    private final OrderService orderService;
    private final TianggeChannelService tianggeChannelService;
    private final ProcessedTianggeOrderRepository processedOrderRepository;
    private final edu.cit.sanico.inventory.InventoryService inventoryService;

    BackorderResolutionListener(
            BackorderTracker backorderTracker,
            OrderService orderService,
            TianggeChannelService tianggeChannelService,
            ProcessedTianggeOrderRepository processedOrderRepository,
            edu.cit.sanico.inventory.InventoryService inventoryService
    ) {
        this.backorderTracker = backorderTracker;
        this.orderService = orderService;
        this.tianggeChannelService = tianggeChannelService;
        this.processedOrderRepository = processedOrderRepository;
        this.inventoryService = inventoryService;
    }

    @EventListener
    @Async
    public void onInventoryUpdated(InventoryUpdatedEvent event) {
        if (event.newStock() <= 0) return;

        Map<String, BackorderTracker.BackorderEntry> pending = backorderTracker.getPendingBackorders();
        if (pending.isEmpty()) {
            try {
                var items = inventoryService.getAllItems();
                var stockList = items.stream()
                        .map(i -> new TianggeChannelService.StockItem(i.getProductId(), i.getStock()))
                        .toList();
                tianggeChannelService.updateStocks(stockList);
            } catch (Exception ignored) {}
            return;
        }

        log.info("[BACKORDER] Inventory updated for {} (stock={}). Attempting resolution of {} pending backorders...",
                event.productId(), event.newStock(), pending.size());

        for (Map.Entry<String, BackorderTracker.BackorderEntry> entry : pending.entrySet()) {
            String tianggeOrderId = entry.getKey();
            BackorderTracker.BackorderEntry backorder = entry.getValue();

            boolean involvesProduct = backorder.items().stream()
                    .anyMatch(i -> event.productId().equals(i.productId()));

            if (involvesProduct) {
                OrderRequest orderReq = new OrderRequest(backorder.items());
                OrderResponse res = orderService.placeOrder(orderReq);

                if ("CONFIRMED".equalsIgnoreCase(res.status())) {
                    log.info("[BACKORDER] Backorder resolved for Tiangge order {}! Transitioning to ACCEPTED", tianggeOrderId);
                    tianggeChannelService.resolveBackorder(tianggeOrderId, "ACCEPTED");
                    processedOrderRepository.save(new ProcessedTianggeOrder(tianggeOrderId, res.orderId(), "ACCEPTED"));
                    backorderTracker.removeBackorder(tianggeOrderId);

                    try {
                        var items = inventoryService.getAllItems();
                        var stockList = items.stream()
                                .map(i -> new TianggeChannelService.StockItem(i.getProductId(), i.getStock()))
                                .toList();
                        tianggeChannelService.updateStocks(stockList);
                    } catch (Exception ignored) {}
                } else {
                    log.info("[BACKORDER] Backorder {} still cannot be fulfilled: {}", tianggeOrderId, res.reason());
                }
            }
        }
    }

    private String getSupplierSku(String productId) {
        return switch (productId) {
            case "P100" -> "JLN-2606";
            case "P200" -> "JLN-6772";
            case "P300" -> "JLN-7018";
            default -> "UNKNOWN";
        };
    }
}
