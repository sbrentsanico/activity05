package edu.cit.sanico.supplier;

interface LegacySupplyApi {
    XmlCatalog fetchCatalog();
    XmlPurchaseOrderAck placeOrder(XmlPurchaseOrder order, String requestId);
    XmlPurchaseOrderStatus getOrderStatus(String poNumber);
}
