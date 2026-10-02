package edu.cit.sanico.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.sanico.inventory.InventoryService;
import edu.cit.sanico.shop.OrderRequest;
import edu.cit.sanico.shop.OrderResponse;
import edu.cit.sanico.shop.OrderService;
import edu.cit.sanico.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Component
class TianggeFeedPoller {

    private static final Logger log = LoggerFactory.getLogger(TianggeFeedPoller.class);

    private static final Map<String, String> SKU_TO_PRODUCT = Map.of(
            "JLN-2606", "P100",
            "JLN-6772", "P200",
            "JLN-7018", "P300"
    );

    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final InstanceIdHolder instanceIdHolder;
    private final FeedCursorRepository feedCursorRepository;
    private final ProcessedTianggeOrderRepository processedOrderRepository;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final BackorderTracker backorderTracker;
    private final TianggeChannelService tianggeChannelService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final HttpClient httpClient;
    private final ObjectMapper jsonMapper;

    TianggeFeedPoller(
            @Value("${tiangge.base-url:https://legacysupply.onrender.com/tiangge/v1}") String baseUrl,
            @Value("${tiangge.client-id:23-1577-502}") String clientId,
            @Value("${tiangge.api-key:LSK-3BA1916A98C945FC7576}") String apiKey,
            InstanceIdHolder instanceIdHolder,
            FeedCursorRepository feedCursorRepository,
            ProcessedTianggeOrderRepository processedOrderRepository,
            OrderService orderService,
            InventoryService inventoryService,
            SupplierGateway supplierGateway,
            BackorderTracker backorderTracker,
            TianggeChannelService tianggeChannelService,
            org.springframework.context.ApplicationEventPublisher eventPublisher
    ) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.instanceIdHolder = instanceIdHolder;
        this.feedCursorRepository = feedCursorRepository;
        this.processedOrderRepository = processedOrderRepository;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.backorderTracker = backorderTracker;
        this.tianggeChannelService = tianggeChannelService;
        this.eventPublisher = eventPublisher;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.jsonMapper = new ObjectMapper();
    }

    @Scheduled(fixedDelay = 2000)
    public void pollFeed() {
        try {
            FeedCursor cursorEntity = feedCursorRepository.findById("tiangge-feed")
                    .orElseGet(() -> new FeedCursor("tiangge-feed", "0"));

            String cursor = cursorEntity.getLastCursor();
            String feedUrl = baseUrl + "/feed?after=" + cursor + "&limit=50";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(feedUrl))
                    .header("Accept", "application/json")
                    .header("X-Client-Id", clientId)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-Client-Instance", instanceIdHolder.getInstanceId())
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                JsonOrderFeed feed = jsonMapper.readValue(response.body(), JsonOrderFeed.class);

                if (feed.events() != null && !feed.events().isEmpty()) {
                    log.info("[FEED] Fetched {} new events from Tiangge feed (cursor={})", feed.events().size(), cursor);

                    for (JsonFeedEvent event : feed.events()) {
                        try {
                            processFeedEvent(event);
                            if (event.seq() != null) {
                                cursorEntity.setLastCursor(String.valueOf(event.seq()));
                                feedCursorRepository.save(cursorEntity);
                            }
                        } catch (Exception ex) {
                            log.error("[FEED] Failed processing feed event {}: {}", event.eventId(), ex.getMessage(), ex);
                        }
                    }

                    if (feed.nextCursor() != null) {
                        String newCursorStr = String.valueOf(feed.nextCursor());
                        if (!newCursorStr.isBlank()) {
                            cursorEntity.setLastCursor(newCursorStr);
                            feedCursorRepository.save(cursorEntity);
                        }
                    }
                }
            } else if (response.statusCode() != 200) {
                log.warn("[FEED] Feed poll returned HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("[FEED] Error polling Tiangge feed: {}", e.getMessage());
        }
    }

    private void processFeedEvent(JsonFeedEvent event) {
        if ("ORDER_CANCELLED".equalsIgnoreCase(event.type()) || "CANCELLED".equalsIgnoreCase(event.type()) || "CANCELLATION".equalsIgnoreCase(event.type())) {
            handleCancellation(event);
            return;
        }

        if (event.orderId() == null || event.orderId().isBlank()) return;

        // Task 4 Deduplication
        if (processedOrderRepository.existsById(event.orderId())) {
            log.info("[FEED] Tiangge order {} already processed, skipping duplicate delivery", event.orderId());
            return;
        }

        log.info("[FEED] Processing new Tiangge order: {}", event.orderId());

        List<OrderRequest.LineItemRequest> itemRequests = new ArrayList<>();
        if (event.lines() != null) {
            for (JsonFeedLine line : event.lines()) {
                String productId = resolveProductId(line.sellerSku());
                if (productId != null) {
                    itemRequests.add(new OrderRequest.LineItemRequest(productId, line.qty()));
                }
            }
        }

        if (itemRequests.isEmpty()) {
            tianggeChannelService.reportDecision(event.orderId(), "REJECTED", null, "Unknown SKUs or empty order");
            processedOrderRepository.save(new ProcessedTianggeOrder(event.orderId(), null, "REJECTED"));
            return;
        }

        OrderRequest orderReq = new OrderRequest(itemRequests);
        OrderResponse orderRes = orderService.placeOrder(orderReq);

        if ("CONFIRMED".equalsIgnoreCase(orderRes.status())) {
            String shopOrderId = orderRes.orderId() != null ? orderRes.orderId().toString() : null;
            tianggeChannelService.reportDecision(event.orderId(), "ACCEPTED", shopOrderId, null);
            processedOrderRepository.save(new ProcessedTianggeOrder(event.orderId(), orderRes.orderId(), "ACCEPTED"));
            syncAllStockToTiangge();
        } else {
            // Check if restock is already on the way for all requested items
            boolean hasIncomingRestock = true;
            for (OrderRequest.LineItemRequest item : itemRequests) {
                if (!backorderTracker.isReorderInFlight(item.productId())) {
                    hasIncomingRestock = false;
                    try {
                        var inv = inventoryService.getItem(item.productId());
                        if (inv.getStock() < item.quantity()) {
                            eventPublisher.publishEvent(new edu.cit.sanico.inventory.event.LowStockEvent(
                                    inv.getProductId(),
                                    inv.getName(),
                                    inv.getStock(),
                                    5
                            ));
                        }
                    } catch (Exception ignored) {}
                }
            }

            String decision = hasIncomingRestock ? "BACKORDERED" : "REJECTED";
            if (hasIncomingRestock) {
                backorderTracker.registerBackorder(event.orderId(), itemRequests);
            }

            String shopOrderId = orderRes.orderId() != null ? orderRes.orderId().toString() : null;
            tianggeChannelService.reportDecision(event.orderId(), decision, shopOrderId, orderRes.reason());
            processedOrderRepository.save(new ProcessedTianggeOrder(event.orderId(), orderRes.orderId(), decision));
        }
    }

    private void syncAllStockToTiangge() {
        try {
            var items = inventoryService.getAllItems();
            var stockList = items.stream()
                    .map(i -> new TianggeChannelService.StockItem(i.getProductId(), i.getStock()))
                    .toList();
            tianggeChannelService.updateStocks(stockList);
        } catch (Exception e) {
            log.error("[FEED] Failed to sync stock to Tiangge: {}", e.getMessage());
        }
    }

    private String resolveProductId(String sku) {
        if (sku == null) return null;
        if (SKU_TO_PRODUCT.containsKey(sku)) {
            return SKU_TO_PRODUCT.get(sku);
        }
        if (SKU_TO_PRODUCT.containsValue(sku)) {
            return sku; // sku is already P100, P200, or P300
        }
        return sku;
    }

    private void handleCancellation(JsonFeedEvent event) {
        if (event.orderId() == null || event.orderId().isBlank()) return;
        log.info("[FEED] Processing cancellation event for Tiangge order {}", event.orderId());

        boolean restocked = false;
        Optional<ProcessedTianggeOrder> existingOrderOpt = processedOrderRepository.findById(event.orderId());
        if (existingOrderOpt.isPresent()) {
            ProcessedTianggeOrder pOrder = existingOrderOpt.get();
            if (pOrder.getLocalOrderId() != null && "ACCEPTED".equals(pOrder.getDecision())) {
                try {
                    orderService.cancelOrder(pOrder.getLocalOrderId());
                    restocked = true;
                    log.info("[FEED] Locally cancelled & restocked order {} for Tiangge order {}", pOrder.getLocalOrderId(), event.orderId());
                } catch (Exception e) {
                    log.warn("[FEED] Failed to cancel local order: {}", e.getMessage());
                }
            }
        }

        // 1. Confirm cancellation FIRST
        tianggeChannelService.confirmCancellation(event.orderId(), restocked);

        // 2. Publish stock AFTER confirmation
        syncAllStockToTiangge();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record JsonOrderFeed(
            @JsonProperty("events") List<JsonFeedEvent> events,
            @JsonProperty("nextCursor") Object nextCursor
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record JsonFeedEvent(
            @JsonProperty("seq") Long seq,
            @JsonProperty("eventId") String eventId,
            @JsonProperty("type") String type,
            @JsonProperty("orderId") String orderId,
            @JsonProperty("placedAt") String placedAt,
            @JsonProperty("decisionDeadline") String decisionDeadline,
            @JsonProperty("cancelledAt") String cancelledAt,
            @JsonProperty("confirmDeadline") String confirmDeadline,
            @JsonProperty("lines") List<JsonFeedLine> lines
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record JsonFeedLine(
            @JsonProperty("sellerSku") String sellerSku,
            @JsonProperty("qty") int qty
    ) {}
}

