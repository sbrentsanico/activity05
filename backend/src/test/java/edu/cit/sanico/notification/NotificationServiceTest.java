package edu.cit.sanico.notification;

import edu.cit.sanico.inventory.event.LowStockEvent;
import edu.cit.sanico.shop.event.OrderPlacedEvent;
import edu.cit.sanico.shop.event.OrderRejectedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private NotificationRepository notificationRepository;
    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        notificationService = new NotificationServiceImpl(notificationRepository);
    }

    @Test
    void onOrderPlaced_logsConfirmedMessage() {
        UUID orderId = UUID.randomUUID();
        OrderPlacedEvent event = new OrderPlacedEvent(orderId, List.of(new OrderPlacedEvent.ItemInfo("P100", 2)));

        notificationService.onOrderPlaced(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertTrue(captor.getValue().getMessage().contains("confirmed"));
        assertTrue(captor.getValue().getMessage().contains(orderId.toString()));
    }

    @Test
    void onOrderRejected_logsRejectedMessage() {
        UUID orderId = UUID.randomUUID();
        OrderRejectedEvent event = new OrderRejectedEvent(orderId, "Insufficient stock", List.of());

        notificationService.onOrderRejected(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertTrue(captor.getValue().getMessage().contains("rejected"));
        assertTrue(captor.getValue().getMessage().contains("Insufficient stock"));
    }

    @Test
    void onLowStock_logsReorderMessage() {
        LowStockEvent event = new LowStockEvent("P200", "Mechanical Keyboard", 3, 5);

        notificationService.onLowStock(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertTrue(captor.getValue().getMessage().contains("Low stock alert"));
        assertTrue(captor.getValue().getMessage().contains("reorder needed"));
        assertTrue(captor.getValue().getMessage().contains("P200"));
    }
}
