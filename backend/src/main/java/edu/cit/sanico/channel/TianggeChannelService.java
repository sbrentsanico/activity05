package edu.cit.sanico.channel;

public interface TianggeChannelService {

    record ListingItem(String sellerSku, String title, String supplierSku) {}
    record StockItem(String sellerSku, int available) {}

    /**
     * Publishes multiple product listings on Tiangge in a single request.
     * Note: PUT /listings replaces any previously published listings.
     */
    void publishListings(java.util.List<ListingItem> listings);

    /**
     * Publishes a single product listing on Tiangge.
     */
    void publishListing(String sellerSku, String title, String supplierSku);

    /**
     * Updates multiple stock entries on Tiangge.
     */
    void updateStocks(java.util.List<StockItem> stocks);

    /**
     * Updates real-time stock numbers on Tiangge for a given product seller SKU.
     */
    void updateStock(String sellerSku, int available);

    /**
     * Sends heartbeat ping to Tiangge with instance header.
     */
    void sendHeartbeat();

    /**
     * Reports an order decision (ACCEPTED, REJECTED, BACKORDERED) to Tiangge.
     */
    void reportDecision(String tianggeOrderId, String decision, String shopOrderId, String reason);

    /**
     * Resolves a pending backorder (ACCEPTED or CANCELLED) to Tiangge.
     */
    void resolveBackorder(String tianggeOrderId, String status);

    /**
     * Confirms customer cancellation to Tiangge.
     */
    void confirmCancellation(String tianggeOrderId, boolean restocked);
}

