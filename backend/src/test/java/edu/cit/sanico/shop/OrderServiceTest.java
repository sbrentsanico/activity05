package edu.cit.sanico.shop;

import edu.cit.sanico.inventory.InventoryItem;
import edu.cit.sanico.inventory.InventoryService;
import edu.cit.sanico.inventory.ReserveResult;
import edu.cit.sanico.shop.event.OrderPlacedEvent;
import edu.cit.sanico.shop.event.OrderRejectedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {

    private InventoryService inventoryService;
    private OrderRepository orderRepository;
    private ApplicationEventPublisher eventPublisher;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        inventoryService = mock(InventoryService.class);
        orderRepository = mock(OrderRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        orderService = new OrderServiceImpl(inventoryService, orderRepository, eventPublisher);
    }

    private InventoryItem createItem(String id, String name, int stock) {
        InventoryItem item = new InventoryItem();
        item.setProductId(id);
        item.setName(name);
        item.setStock(stock);
        return item;
    }

    @Test
    void placeOrder_allAvailable_confirmsAndReservesAll() {
        when(inventoryService.getItem("P100")).thenReturn(createItem("P100", "Wireless Mouse", 25));
        when(inventoryService.getItem("P200")).thenReturn(createItem("P200", "Mechanical Keyboard", 10));

        when(inventoryService.reserve("P100", 2))
                .thenReturn(new ReserveResult("CONFIRMED", null, createItem("P100", "Wireless Mouse", 23)));
        when(inventoryService.reserve("P200", 1))
                .thenReturn(new ReserveResult("CONFIRMED", null, createItem("P200", "Mechanical Keyboard", 9)));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderRequest request = new OrderRequest(List.of(
                new OrderRequest.LineItemRequest("P100", 2),
                new OrderRequest.LineItemRequest("P200", 1)
        ));

        OrderResponse response = orderService.placeOrder(request);

        assertEquals("CONFIRMED", response.status());
        assertNull(response.reason());
        assertEquals(2, response.items().size());
        verify(inventoryService).reserve("P100", 2);
        verify(inventoryService).reserve("P200", 1);
        verify(eventPublisher).publishEvent(any(OrderPlacedEvent.class));
    }

    @Test
    void placeOrder_oneItemFailsValidation_rejectsOrderWithNoReservations() {
        when(inventoryService.getItem("P100")).thenReturn(createItem("P100", "Wireless Mouse", 25));
        when(inventoryService.getItem("P300")).thenReturn(createItem("P300", "USB-C Hub", 0)); // 0 stock

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderRequest request = new OrderRequest(List.of(
                new OrderRequest.LineItemRequest("P100", 2),
                new OrderRequest.LineItemRequest("P300", 1) // exceeds available 0
        ));

        OrderResponse response = orderService.placeOrder(request);

        assertEquals("REJECTED", response.status());
        assertTrue(response.reason().contains("Insufficient stock for P300"));
        // Critical: All-or-nothing rollback check - ZERO reservations attempted!
        verify(inventoryService, never()).reserve(anyString(), anyInt());
        verify(eventPublisher).publishEvent(any(OrderRejectedEvent.class));
    }

    @Test
    void cancelOrder_confirmedOrder_setsCancelledAndRestocksAllItems() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setStatus("CONFIRMED");
        order.addItem(new OrderItem(order, "P100", 2));
        order.addItem(new OrderItem(order, "P200", 1));

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder(orderId);

        assertEquals("CANCELLED", response.status());
        verify(inventoryService).restock("P100", 2);
        verify(inventoryService).restock("P200", 1);
    }

    @Test
    void cancelOrder_nonExistent_throws404() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> orderService.cancelOrder(orderId));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void cancelOrder_alreadyCancelled_throws409() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setStatus("CANCELLED");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> orderService.cancelOrder(orderId));
        assertEquals(409, ex.getStatusCode().value());
    }
}
