package edu.cit.sanico.supplier;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
class LegacySupplyTranslator {

    record SkuPackInfo(String supplierSku, int packSize, String description) {}

    private final Map<String, SkuPackInfo> productToSupplierMap = new ConcurrentHashMap<>();
    private final Map<String, String> supplierSkuToProductMap = new ConcurrentHashMap<>();

    LegacySupplyTranslator() {
        // Known mappings for the standard shop inventory items
        registerMapping("P100", "JLN-2606", 6, "WIRELESS MOUSE 2.4GHZ");
        registerMapping("P200", "JLN-6772", 24, "KEYBOARD MECH TKL");
        registerMapping("P300", "JLN-7018", 20, "USB HUB 4-PORT");
    }

    void registerMapping(String productId, String supplierSku, int packSize, String description) {
        SkuPackInfo info = new SkuPackInfo(supplierSku, packSize, description);
        productToSupplierMap.put(productId, info);
        supplierSkuToProductMap.put(supplierSku, productId);
    }

    void updateFromCatalog(XmlCatalog catalog) {
        if (catalog == null || catalog.items() == null) return;
        for (XmlCatalogItem item : catalog.items()) {
            if (item.supplierSku() == null) continue;
            String desc = item.description() != null ? item.description().toUpperCase() : "";
            int packSize = item.packSize() > 0 ? item.packSize() : 1;

            if (desc.contains("MOUSE") && !productToSupplierMap.containsKey("P100")) {
                registerMapping("P100", item.supplierSku(), packSize, item.description());
            } else if (desc.contains("KEYBOARD") && !productToSupplierMap.containsKey("P200")) {
                registerMapping("P200", item.supplierSku(), packSize, item.description());
            } else if (desc.contains("HUB") && !productToSupplierMap.containsKey("P300")) {
                registerMapping("P300", item.supplierSku(), packSize, item.description());
            }
        }
    }

    SkuPackInfo getSupplierInfo(String productId) {
        SkuPackInfo info = productToSupplierMap.get(productId);
        if (info == null) {
            throw new SupplierException("No supplier item mapping found for product: " + productId);
        }
        return info;
    }

    int calculateOrderCases(int unitsNeeded, int packSize) {
        if (packSize <= 0) packSize = 1;
        int cases = (int) Math.ceil((double) unitsNeeded / packSize);
        if (cases < 1) cases = 1;
        if (cases > 99) cases = 99;
        return cases;
    }

    SupplierOrderStatus toDomainStatus(String statusCode) {
        if (statusCode == null) return SupplierOrderStatus.UNKNOWN;
        return switch (statusCode.trim()) {
            case "10" -> SupplierOrderStatus.ACCEPTED;
            case "20" -> SupplierOrderStatus.PICKING;
            case "30" -> SupplierOrderStatus.SHIPPED;
            case "40" -> SupplierOrderStatus.DELIVERED;
            case "CANCELLED", "CANCELED", "99" -> SupplierOrderStatus.CANCELLED;
            default -> SupplierOrderStatus.UNKNOWN;
        };
    }

    SupplierOrderResult toDomainResult(XmlPurchaseOrderAck ack, String productId, int unitsRequested) {
        SkuPackInfo info = productToSupplierMap.get(productId);
        int packSize = info != null ? info.packSize() : 1;
        int cases = ack.qty();
        int unitsOrdered = cases * packSize;

        return new SupplierOrderResult(
            ack.poNumber(),
            productId,
            unitsRequested,
            unitsOrdered,
            toDomainStatus(ack.statusCode()),
            ack.buyerRef(),
            parseInstant(ack.createdAt()),
            "Order acknowledged by supplier: " + ack.poNumber()
        );
    }

    SupplierOrderResult toDomainResult(XmlPurchaseOrderStatus status) {
        String productId = supplierSkuToProductMap.getOrDefault(status.supplierSku(), "UNKNOWN");
        SkuPackInfo info = productToSupplierMap.get(productId);
        int packSize = info != null ? info.packSize() : 1;
        int cases = status.qty();
        int unitsOrdered = cases * packSize;

        return new SupplierOrderResult(
            status.poNumber(),
            productId,
            unitsOrdered,
            unitsOrdered,
            toDomainStatus(status.statusCode()),
            status.buyerRef(),
            parseInstant(status.createdAt()),
            "Order status: " + status.statusCode() + " (" + toDomainStatus(status.statusCode()) + ")"
        );
    }

    private Instant parseInstant(String ts) {
        if (ts == null || ts.isBlank()) return Instant.now();
        try {
            return Instant.parse(ts);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
