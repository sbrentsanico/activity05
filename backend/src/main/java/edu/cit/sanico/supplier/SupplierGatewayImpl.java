package edu.cit.sanico.supplier;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
class SupplierGatewayImpl implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(SupplierGatewayImpl.class);

    private final LegacySupplyApi client;
    private final LegacySupplyTranslator translator;

    SupplierGatewayImpl(LegacySupplyApi client, LegacySupplyTranslator translator) {
        this.client = client;
        this.translator = translator;
    }

    @PostConstruct
    void init() {
        try {
            XmlCatalog catalog = client.fetchCatalog();
            translator.updateFromCatalog(catalog);
            log.info("[LegacySupply] Catalog initialized with {} items.",
                catalog != null && catalog.items() != null ? catalog.items().size() : 0);
        } catch (Exception e) {
            log.warn("[LegacySupply] Initial catalog fetch deferred: {}", e.getMessage());
        }
    }

    @Override
    public SupplierOrderResult placeReorder(String productId, int unitsNeeded, String buyerRef) {
        LegacySupplyTranslator.SkuPackInfo skuInfo = translator.getSupplierInfo(productId);
        int cases = translator.calculateOrderCases(unitsNeeded, skuInfo.packSize());

        if (buyerRef == null || buyerRef.isBlank()) {
            buyerRef = "REORDER-" + productId + "-" + System.currentTimeMillis();
        }
        if (buyerRef.length() > 40) {
            buyerRef = buyerRef.substring(0, 40);
        }

        // Generates an idempotency key up to 80 characters
        String requestId = "req-" + UUID.randomUUID().toString();

        log.info("[LegacySupply] Placing purchase order: Product={}, SupplierSku={}, UnitsNeeded={}, PackSize={}, OrderQtyCases={}, BuyerRef={}, RequestId={}",
            productId, skuInfo.supplierSku(), unitsNeeded, skuInfo.packSize(), cases, buyerRef, requestId);

        XmlPurchaseOrder orderReq = new XmlPurchaseOrder(skuInfo.supplierSku(), cases, buyerRef);
        XmlPurchaseOrderAck ack = client.placeOrder(orderReq, requestId);

        SupplierOrderResult result = translator.toDomainResult(ack, productId, unitsNeeded);
        log.info("[LegacySupply] Order placed successfully: PoNumber={}, Status={}, OrderedUnits={}",
            result.supplierOrderId(), result.status(), result.unitsOrdered());
        return result;
    }

    @Override
    public SupplierOrderResult checkOrderStatus(String supplierOrderId) {
        XmlPurchaseOrderStatus status = client.getOrderStatus(supplierOrderId);
        return translator.toDomainResult(status);
    }

    @Override
    public SupplierOrderResult trackOrderToDelivery(String supplierOrderId) {
        int maxPolls = 30;
        long politeDelayMs = 3000; // 3.0s polite delay ensures zero rate limiting

        SupplierOrderResult currentResult;
        try {
            currentResult = checkOrderStatus(supplierOrderId);
            log.info("[LegacySupply] Tracking order {}: initial status = {}", supplierOrderId, currentResult.status());
            if (currentResult.status() == SupplierOrderStatus.DELIVERED ||
                currentResult.status() == SupplierOrderStatus.CANCELLED) {
                return currentResult;
            }
        } catch (SupplierException se) {
            log.warn("[LegacySupply] Could not fetch initial status for {}: {}. Will poll in next cycles.",
                supplierOrderId, se.getMessage());
            currentResult = new SupplierOrderResult(supplierOrderId, "UNKNOWN", 0, 0, SupplierOrderStatus.ACCEPTED, "", null, se.getMessage());
        }

        for (int poll = 1; poll <= maxPolls; poll++) {
            try {
                Thread.sleep(politeDelayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("[LegacySupply] Polling interrupted for order {}", supplierOrderId);
                return currentResult;
            }

            try {
                currentResult = checkOrderStatus(supplierOrderId);
                log.info("[LegacySupply] Poll {}/{}: Order {} status = {}", poll, maxPolls, supplierOrderId, currentResult.status());

                if (currentResult.status() == SupplierOrderStatus.DELIVERED ||
                    currentResult.status() == SupplierOrderStatus.CANCELLED) {
                    log.info("[LegacySupply] Order {} reached terminal state: {}", supplierOrderId, currentResult.status());
                    return currentResult;
                }
            } catch (SupplierException se) {
                log.warn("[LegacySupply] Transient poll error on cycle {} for {}: {}. Continuing next cycle.",
                    poll, supplierOrderId, se.getMessage());
            }
        }

        log.warn("[LegacySupply] Order {} did not reach terminal status within {} polls (current: {})",
            supplierOrderId, maxPolls, currentResult.status());
        return currentResult;
    }
}
