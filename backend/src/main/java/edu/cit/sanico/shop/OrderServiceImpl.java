package edu.cit.sanico.shop;

import edu.cit.sanico.inventory.InventoryItem;
import edu.cit.sanico.inventory.InventoryService;
import edu.cit.sanico.inventory.ReserveResult;
import edu.cit.sanico.shop.event.OrderPlacedEvent;
import edu.cit.sanico.shop.event.OrderRejectedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
class OrderServiceImpl implements OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    OrderServiceImpl(
            InventoryService inventoryService,
            OrderRepository orderRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            return new OrderResponse(
                    null,
                    "REJECTED",
                    "Order must contain at least one line item.",
                    List.of(),
                    fetchCurrentInventory()
            );
        }

        // Aggregate quantities per product to handle duplicate product lines in cart
        Map<String, Integer> requestedTotals = new LinkedHashMap<>();
        for (OrderRequest.LineItemRequest itemReq : request.items()) {
            if (itemReq.productId() == null || itemReq.productId().isBlank()) {
                return rejectOrder(request, "Product ID cannot be blank.");
            }
            if (itemReq.quantity() <= 0) {
                return rejectOrder(request, "Quantity must be greater than zero for product: " + itemReq.productId());
            }
            requestedTotals.merge(itemReq.productId(), itemReq.quantity(), Integer::sum);
        }

        // Pre-validation: check stock for every item before reserving anything (all-or-nothing)
        for (Map.Entry<String, Integer> entry : requestedTotals.entrySet()) {
            String productId = entry.getKey();
            int totalQty = entry.getValue();

            InventoryItem inventoryItem;
            try {
                inventoryItem = inventoryService.getItem(productId);
            } catch (Exception e) {
                return rejectOrder(request, "Product not found: " + productId);
            }

            if (inventoryItem.getStock() < totalQty) {
                String reason = "Insufficient stock for " + productId + " (" + inventoryItem.getName() + "). "
                        + "Requested: " + totalQty + ", Available: " + inventoryItem.getStock();
                return rejectOrder(request, reason);
            }
        }

        // All items passed validation -> proceed to reserve each line item
        for (OrderRequest.LineItemRequest itemReq : request.items()) {
            ReserveResult reserveResult = inventoryService.reserve(itemReq.productId(), itemReq.quantity());
            if (!"CONFIRMED".equals(reserveResult.status())) {
                // Safeguard against concurrent race condition: trigger transaction rollback
                throw new IllegalStateException("Unexpected reservation failure during order placement: "
                        + reserveResult.reason());
            }
        }

        // Persist confirmed order and items
        Order order = new Order();
        order.setStatus("CONFIRMED");
        order.setReason(null);

        for (OrderRequest.LineItemRequest itemReq : request.items()) {
            order.addItem(new OrderItem(order, itemReq.productId(), itemReq.quantity()));
        }

        Order savedOrder = orderRepository.save(order);

        // Publish OrderPlaced domain event
        List<OrderPlacedEvent.ItemInfo> eventItems = request.items().stream()
                .map(i -> new OrderPlacedEvent.ItemInfo(i.productId(), i.quantity()))
                .toList();
        eventPublisher.publishEvent(new OrderPlacedEvent(savedOrder.getOrderId(), eventItems));

        // Build item outcomes
        List<OrderResponse.ItemOutcome> itemOutcomes = request.items().stream()
                .map(i -> new OrderResponse.ItemOutcome(i.productId(), "CONFIRMED"))
                .toList();

        return new OrderResponse(
                savedOrder.getOrderId(),
                "CONFIRMED",
                null,
                itemOutcomes,
                fetchCurrentInventory()
        );
    }

    private OrderResponse rejectOrder(OrderRequest request, String reason) {
        Order rejectedOrder = new Order();
        rejectedOrder.setStatus("REJECTED");
        rejectedOrder.setReason(reason);

        for (OrderRequest.LineItemRequest itemReq : request.items()) {
            rejectedOrder.addItem(new OrderItem(rejectedOrder, itemReq.productId(), itemReq.quantity()));
        }

        Order savedOrder = orderRepository.save(rejectedOrder);

        // Publish OrderRejected domain event
        List<OrderRejectedEvent.ItemInfo> eventItems = request.items().stream()
                .map(i -> new OrderRejectedEvent.ItemInfo(i.productId(), i.quantity()))
                .toList();
        eventPublisher.publishEvent(new OrderRejectedEvent(savedOrder.getOrderId(), reason, eventItems));

        List<OrderResponse.ItemOutcome> itemOutcomes = request.items().stream()
                .map(i -> new OrderResponse.ItemOutcome(i.productId(), "REJECTED"))
                .toList();

        return new OrderResponse(
                savedOrder.getOrderId(),
                "REJECTED",
                reason,
                itemOutcomes,
                fetchCurrentInventory()
        );
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderId));

        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is already cancelled.");
        }

        if (!"CONFIRMED".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only CONFIRMED orders can be cancelled. Current status: " + order.getStatus());
        }

        // Return reserved items back to inventory stock
        for (OrderItem item : order.getItems()) {
            inventoryService.restock(item.getProductId(), item.getQuantity());
        }

        order.setStatus("CANCELLED");
        order.setReason("Order cancelled by user. Stock returned.");
        Order savedOrder = orderRepository.save(order);

        List<OrderResponse.ItemOutcome> itemOutcomes = savedOrder.getItems().stream()
                .map(i -> new OrderResponse.ItemOutcome(i.getProductId(), "RESTOCKED"))
                .toList();

        return new OrderResponse(
                savedOrder.getOrderId(),
                "CANCELLED",
                savedOrder.getReason(),
                itemOutcomes,
                fetchCurrentInventory()
        );
    }

    @Override
    public List<OrderHistoryResponse> getOrderHistory() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(o -> new OrderHistoryResponse(
                        o.getOrderId(),
                        o.getStatus(),
                        o.getReason(),
                        o.getCreatedAt(),
                        o.getItems().stream()
                                .map(i -> new OrderHistoryResponse.OrderItemDto(
                                        i.getItemId(),
                                        i.getProductId(),
                                        i.getQuantity()))
                                .toList()
                ))
                .toList();
    }

    private List<OrderResponse.InventoryInfo> fetchCurrentInventory() {
        return inventoryService.getAllItems().stream()
                .map(i -> new OrderResponse.InventoryInfo(i.getProductId(), i.getName(), i.getStock()))
                .toList();
    }
}