package edu.cit.sanico.shop;

import edu.cit.sanico.inventory.InventoryService;
import edu.cit.sanico.inventory.ReserveResult;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl implements OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    public OrderServiceImpl(InventoryService inventoryService, OrderRepository orderRepository) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    @Override
    public OrderResponse placeOrder(String productId, int quantity) {
        ReserveResult result = inventoryService.reserve(productId, quantity);

        Order order = new Order();
        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setStatus(result.status());
        order.setReason(result.reason());
        orderRepository.save(order);

        OrderResponse.InventoryInfo inventoryInfo = null;
        if (result.item() != null) {
            inventoryInfo = new OrderResponse.InventoryInfo(
                    result.item().getProductId(),
                    result.item().getName(),
                    result.item().getStock()
            );
        }
        return new OrderResponse(result.status(), result.reason(), inventoryInfo);
    }
}