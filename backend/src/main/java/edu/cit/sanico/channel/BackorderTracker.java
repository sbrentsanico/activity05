package edu.cit.sanico.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BackorderTracker {

    private static final Logger log = LoggerFactory.getLogger(BackorderTracker.class);

    public record BackorderEntry(String tianggeOrderId, java.util.List<edu.cit.sanico.shop.OrderRequest.LineItemRequest> items) {}

    private final Set<String> activeReorders = ConcurrentHashMap.newKeySet();
    private final Map<String, BackorderEntry> pendingBackorders = new ConcurrentHashMap<>();

    public void markReorderInFlight(String productId) {
        activeReorders.add(productId);
        log.info("[BACKORDER] Marked reorder in flight for product {}", productId);
    }

    public void clearReorderInFlight(String productId) {
        activeReorders.remove(productId);
        log.info("[BACKORDER] Cleared reorder in flight for product {}", productId);
    }

    public boolean isReorderInFlight(String productId) {
        return activeReorders.contains(productId);
    }

    public void registerBackorder(String tianggeOrderId, java.util.List<edu.cit.sanico.shop.OrderRequest.LineItemRequest> items) {
        pendingBackorders.put(tianggeOrderId, new BackorderEntry(tianggeOrderId, items));
        log.info("[BACKORDER] Registered backorder for Tiangge order {} with {} items", tianggeOrderId, items.size());
    }

    public Map<String, BackorderEntry> getPendingBackorders() {
        return pendingBackorders;
    }

    public void removeBackorder(String tianggeOrderId) {
        pendingBackorders.remove(tianggeOrderId);
        log.info("[BACKORDER] Removed backorder for Tiangge order {}", tianggeOrderId);
    }
}
