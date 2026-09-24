package edu.cit.sanico.supplier;

import org.junit.jupiter.api.Test;

class LiveLegacySupplyTest {

    @Test
    void executeLiveChecks() throws Exception {
        String clientId = "23-1577-502";
        String apiKey = "LSK-3BA1916A98C945FC7576";
        String baseUrl = "https://legacysupply.onrender.com/api/v1";

        LegacySupplySessionManager sessionManager = new LegacySupplySessionManager();
        LegacySupplyClient client = new LegacySupplyClient(baseUrl, clientId, apiKey, sessionManager);
        LegacySupplyTranslator translator = new LegacySupplyTranslator();
        SupplierGateway gateway = new SupplierGatewayImpl(client, translator);

        System.out.println("=== 1. Checking Catalog ===");
        XmlCatalog catalog = client.fetchCatalog();
        translator.updateFromCatalog(catalog);
        System.out.println("Catalog items: " + catalog.items().size());

        System.out.println("\n=== 2. Placing Purchase Order 3 for P300 ===");
        String buyerRef = "ORDER-P300-" + System.currentTimeMillis();
        SupplierOrderResult placed = gateway.placeReorder("P300", 20, buyerRef);
        System.out.println("Order 3 placed: PO=" + placed.supplierOrderId() + ", Status=" + placed.status() + ", Units=" + placed.unitsOrdered());

        System.out.println("\n=== 3. Tracking PO-100008 to Delivered ===");
        SupplierOrderResult track1 = gateway.trackOrderToDelivery("PO-100008");
        System.out.println("PO-100008 status: " + track1.status());

        if (track1.status() != SupplierOrderStatus.DELIVERED) {
            System.out.println("\n=== 4. Tracking " + placed.supplierOrderId() + " to Delivered ===");
            SupplierOrderResult track3 = gateway.trackOrderToDelivery(placed.supplierOrderId());
            System.out.println(placed.supplierOrderId() + " status: " + track3.status());
        }

        System.out.println("\n=== Live verification run completed! ===");
    }
}
