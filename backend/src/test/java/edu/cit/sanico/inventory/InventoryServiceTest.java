package edu.cit.sanico.inventory;

import edu.cit.sanico.inventory.event.LowStockEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryServiceTest {

    private InventoryRepository inventoryRepository;
    private ApplicationEventPublisher eventPublisher;
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryRepository = mock(InventoryRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        inventoryService = new InventoryServiceImpl(inventoryRepository, eventPublisher, 5);
    }

    private InventoryItem createItem(String id, String name, int stock) {
        InventoryItem item = new InventoryItem();
        item.setProductId(id);
        item.setName(name);
        item.setStock(stock);
        return item;
    }

    @Test
    void reserve_sufficientStock_decrementsAndConfirms() {
        InventoryItem item = createItem("P100", "Wireless Mouse", 25);
        when(inventoryRepository.findById("P100")).thenReturn(Optional.of(item));

        ReserveResult result = inventoryService.reserve("P100", 5);

        assertEquals("CONFIRMED", result.status());
        assertEquals(20, item.getStock());
        verify(inventoryRepository).save(item);
        // Stock is 20, threshold is 5 -> no LowStockEvent
        verify(eventPublisher, never()).publishEvent(any(LowStockEvent.class));
    }

    @Test
    void reserve_stockDropsBelowThreshold_publishesLowStockEvent() {
        InventoryItem item = createItem("P200", "Mechanical Keyboard", 6);
        when(inventoryRepository.findById("P200")).thenReturn(Optional.of(item));

        ReserveResult result = inventoryService.reserve("P200", 3);

        assertEquals("CONFIRMED", result.status());
        assertEquals(3, item.getStock());
        verify(inventoryRepository).save(item);

        ArgumentCaptor<LowStockEvent> captor = ArgumentCaptor.forClass(LowStockEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        LowStockEvent event = captor.getValue();
        assertEquals("P200", event.productId());
        assertEquals(3, event.remainingStock());
        assertEquals(5, event.threshold());
    }

    @Test
    void restock_incrementsStock() {
        InventoryItem item = createItem("P100", "Wireless Mouse", 10);
        when(inventoryRepository.findById("P100")).thenReturn(Optional.of(item));

        inventoryService.restock("P100", 5);

        assertEquals(15, item.getStock());
        verify(inventoryRepository).save(item);
    }
}
