package edu.cit.sanico.notification;

import edu.cit.sanico.inventory.event.LowStockEvent;
import edu.cit.sanico.shop.event.OrderPlacedEvent;
import edu.cit.sanico.shop.event.OrderRejectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @EventListener
    @Transactional
    public void onOrderPlaced(OrderPlacedEvent event) {
        String itemDetails = event.items().stream()
                .map(i -> i.productId() + " (x" + i.quantity() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("no items");

        String message = "Order " + event.orderId() + " confirmed [" + itemDetails + "]";
        notificationRepository.save(new Notification(message));
    }

    @EventListener
    @Transactional
    public void onOrderRejected(OrderRejectedEvent event) {
        String idStr = event.orderId() != null ? event.orderId().toString() : "N/A";
        String message = "Order " + idStr + " rejected: " + event.reason();
        notificationRepository.save(new Notification(message));
    }

    @EventListener
    @Transactional
    public void onLowStock(LowStockEvent event) {
        String message = "Low stock alert: Product " + event.productId() + " (" + event.productName()
                + ") is down to " + event.remainingStock() + " units (threshold: "
                + event.threshold() + ") - reorder needed";
        notificationRepository.save(new Notification(message));
    }

    @Override
    public List<Notification> getAllNotifications() {
        return notificationRepository.findAllByOrderByCreatedAtDesc();
    }
}
