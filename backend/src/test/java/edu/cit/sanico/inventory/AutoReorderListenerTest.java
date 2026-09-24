package edu.cit.sanico.inventory;

import edu.cit.sanico.inventory.event.LowStockEvent;
import edu.cit.sanico.supplier.SupplierGateway;
import edu.cit.sanico.supplier.SupplierOrderResult;
import edu.cit.sanico.supplier.SupplierOrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AutoReorderListenerTest {

    private SupplierGateway mockGateway;
    private InventoryService mockInventoryService;
    private AutoReorderListener listener;

    @BeforeEach
    void setUp() {
        mockGateway = mock(SupplierGateway.class);
        mockInventoryService = mock(InventoryService.class);
        listener = new AutoReorderListener(mockGateway, mockInventoryService);
    }

    @Test
    void onLowStock_placesOrderAndRestocksOnDelivery() {
        LowStockEvent event = new LowStockEvent("P200", "Mechanical Keyboard", 3, 5);

        SupplierOrderResult placedResult = new SupplierOrderResult(
            "PO-100231",
            "P200",
            10,
            24,
            SupplierOrderStatus.ACCEPTED,
            "BUYER-REF",
            Instant.now(),
            "Accepted"
        );

        SupplierOrderResult deliveredResult = new SupplierOrderResult(
            "PO-100231",
            "P200",
            10,
            24,
            SupplierOrderStatus.DELIVERED,
            "BUYER-REF",
            Instant.now(),
            "Delivered"
        );

        when(mockGateway.placeReorder(eq("P200"), anyInt(), anyString())).thenReturn(placedResult);
        when(mockGateway.trackOrderToDelivery("PO-100231")).thenReturn(deliveredResult);

        listener.onLowStock(event);

        verify(mockGateway).placeReorder(eq("P200"), eq(10), anyString());
        verify(mockGateway).trackOrderToDelivery("PO-100231");
        verify(mockInventoryService).restock("P200", 24);
    }
}
