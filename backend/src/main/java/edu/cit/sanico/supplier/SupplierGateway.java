package edu.cit.sanico.supplier;

public interface SupplierGateway {

    /**
     * Places a replenishment purchase order with the supplier for the requested domain product and units.
     * The gateway translates domain product IDs and unit requirements into supplier-specific SKUs and pack sizes.
     *
     * @param productId    Internal product ID (e.g. P100, P200)
     * @param unitsNeeded  Number of individual domain units needed
     * @param buyerRef     Domain reference string (up to 40 characters)
     * @return Result of the purchase order acknowledgement
     */
    SupplierOrderResult placeReorder(String productId, int unitsNeeded, String buyerRef);

    /**
     * Checks the current status of an order placed with the supplier.
     *
     * @param supplierOrderId The supplier order ID (e.g. PO-100231)
     * @return The updated order status and details
     */
    SupplierOrderResult checkOrderStatus(String supplierOrderId);

    /**
     * Tracks an order with polite polling until it reaches a terminal status (DELIVERED or CANCELLED).
     *
     * @param supplierOrderId The supplier order ID
     * @return The final order result
     */
    SupplierOrderResult trackOrderToDelivery(String supplierOrderId);
}
