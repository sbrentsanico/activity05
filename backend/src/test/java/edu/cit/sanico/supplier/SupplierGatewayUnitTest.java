package edu.cit.sanico.supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SupplierGatewayUnitTest {

    private LegacySupplyApi mockClient;
    private LegacySupplyTranslator translator;
    private SupplierGateway supplierGateway;

    @BeforeEach
    void setUp() {
        mockClient = mock(LegacySupplyApi.class);
        translator = new LegacySupplyTranslator();
        supplierGateway = new SupplierGatewayImpl(mockClient, translator);
    }

    @Test
    void packSizeArithmetic_convertsUnitsToWholeCases() {
        // P100 pack size is 6
        assertEquals(1, translator.calculateOrderCases(5, 6));
        assertEquals(1, translator.calculateOrderCases(6, 6));
        assertEquals(2, translator.calculateOrderCases(7, 6));
        assertEquals(2, translator.calculateOrderCases(12, 6));
        assertEquals(3, translator.calculateOrderCases(13, 6));
    }

    @Test
    void placeReorder_translatesInternalProductToSupplierSkuAndSendsRequestId() {
        // Product P100 (Pack size 6), requesting 10 units -> requires 2 cases (12 units)
        XmlPurchaseOrderAck mockAck = new XmlPurchaseOrderAck(
            "PO-99901",
            "10",
            "JLN-2606",
            2,
            "CS",
            "BUYER-TEST",
            Instant.now().toString()
        );

        when(mockClient.placeOrder(any(XmlPurchaseOrder.class), anyString())).thenReturn(mockAck);

        SupplierOrderResult result = supplierGateway.placeReorder("P100", 10, "BUYER-TEST");

        assertNotNull(result);
        assertEquals("PO-99901", result.supplierOrderId());
        assertEquals("P100", result.productId());
        assertEquals(10, result.unitsRequested());
        assertEquals(12, result.unitsOrdered()); // 2 cases * 6 units
        assertEquals(SupplierOrderStatus.ACCEPTED, result.status());

        ArgumentCaptor<XmlPurchaseOrder> orderCaptor = ArgumentCaptor.forClass(XmlPurchaseOrder.class);
        ArgumentCaptor<String> reqIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockClient).placeOrder(orderCaptor.capture(), reqIdCaptor.capture());

        XmlPurchaseOrder sentOrder = orderCaptor.getValue();
        assertEquals("JLN-2606", sentOrder.supplierSku());
        assertEquals(2, sentOrder.qty());
        assertEquals("BUYER-TEST", sentOrder.buyerRef());

        // Verify X-Request-Id header value is present and prefixed
        String requestId = reqIdCaptor.getValue();
        assertNotNull(requestId);
        assertTrue(requestId.startsWith("req-"));
        assertTrue(requestId.length() <= 80);
    }

    @Test
    void checkOrderStatus_translatesStatusToDomainEnum() {
        XmlPurchaseOrderStatus mockStatus = new XmlPurchaseOrderStatus(
            "PO-99901",
            "40",
            "JLN-2606",
            2,
            "CS",
            "BUYER-TEST",
            Instant.now().toString(),
            Instant.now().toString()
        );

        when(mockClient.getOrderStatus("PO-99901")).thenReturn(mockStatus);

        SupplierOrderResult result = supplierGateway.checkOrderStatus("PO-99901");

        assertEquals(SupplierOrderStatus.DELIVERED, result.status());
        assertEquals(12, result.unitsOrdered());
    }
}
