package edu.cit.sanico.supplier;

import java.time.Instant;

public record SupplierOrderResult(
    String supplierOrderId,
    String productId,
    int unitsRequested,
    int unitsOrdered,
    SupplierOrderStatus status,
    String buyerRef,
    Instant createdAt,
    String message
) {}
