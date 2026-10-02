package edu.cit.sanico.channel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;

@Service
class TianggeChannelServiceImpl implements TianggeChannelService {

    private static final Logger log = LoggerFactory.getLogger(TianggeChannelServiceImpl.class);

    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final InstanceIdHolder instanceIdHolder;
    private final HttpClient httpClient;
    private final ObjectMapper jsonMapper;
    private final Instant startedAt;

    TianggeChannelServiceImpl(
            @Value("${tiangge.base-url:https://legacysupply.onrender.com/tiangge/v1}") String baseUrl,
            @Value("${tiangge.client-id:23-1577-502}") String clientId,
            @Value("${tiangge.api-key:LSK-3BA1916A98C945FC7576}") String apiKey,
            InstanceIdHolder instanceIdHolder
    ) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.instanceIdHolder = instanceIdHolder;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.jsonMapper = new ObjectMapper();
        this.startedAt = Instant.now();
    }

    private HttpRequest.Builder createRequestBuilder(String endpoint) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("X-Client-Id", clientId)
                .header("Authorization", "Bearer " + apiKey)
                .header("X-Client-Instance", instanceIdHolder.getInstanceId());
    }

    @Override
    public void publishListings(java.util.List<ListingItem> listings) {
        if (listings == null || listings.isEmpty()) return;
        try {
            var dtoList = listings.stream()
                    .map(l -> new JsonListingRequest(l.sellerSku(), l.title(), l.supplierSku()))
                    .toList();
            String jsonBody = jsonMapper.writeValueAsString(dtoList);

            HttpRequest request = createRequestBuilder("/listings")
                    .timeout(Duration.ofSeconds(20))
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[TIANGGE] Successfully published {} listings", listings.size());
            } else {
                log.warn("[TIANGGE] Listing publish HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("[TIANGGE] Failed to publish listings: {}", e.getMessage());
        }
    }

    @Override
    public void publishListing(String sellerSku, String title, String supplierSku) {
        publishListings(Collections.singletonList(new ListingItem(sellerSku, title, supplierSku)));
    }

    @Override
    public void updateStocks(java.util.List<StockItem> stocks) {
        if (stocks == null || stocks.isEmpty()) return;
        try {
            var dtoList = stocks.stream()
                    .map(s -> new JsonStockUpdateRequest(s.sellerSku(), s.available()))
                    .toList();
            String jsonBody = jsonMapper.writeValueAsString(dtoList);

            HttpRequest request = createRequestBuilder("/stock")
                    .timeout(Duration.ofSeconds(20))
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[TIANGGE] Published updated stock for {} items", stocks.size());
            } else {
                log.warn("[TIANGGE] Stock update HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("[TIANGGE] Failed to update stocks: {}", e.getMessage());
        }
    }

    @Override
    public void updateStock(String sellerSku, int available) {
        updateStocks(Collections.singletonList(new StockItem(sellerSku, available)));
    }

    @Override
    public void sendHeartbeat() {
        try {
            long uptimeSeconds = Duration.between(startedAt, Instant.now()).toSeconds();
            var heartbeat = new JsonHeartbeatRequest("my-shop", startedAt.toString(), uptimeSeconds);
            String jsonBody = jsonMapper.writeValueAsString(heartbeat);

            HttpRequest request = createRequestBuilder("/instances/heartbeat")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[TIANGGE] Heartbeat sent successfully (HTTP {})", response.statusCode());
            } else {
                log.warn("[TIANGGE] Heartbeat returned HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("[TIANGGE] Heartbeat failed: {}", e.getMessage());
        }
    }

    private void sendWithRetry(HttpRequest request, String actionDesc) {
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    log.info("[TIANGGE] {} succeeded (HTTP {})", actionDesc, response.statusCode());
                    return;
                } else if (response.statusCode() == 503 || response.statusCode() == 429) {
                    log.warn("[TIANGGE] {} got HTTP {}, retrying ({}/{})...", actionDesc, response.statusCode(), attempt, maxAttempts);
                    Thread.sleep(1000L * attempt);
                } else {
                    log.warn("[TIANGGE] {} returned HTTP {}: {}", actionDesc, response.statusCode(), response.body());
                    return;
                }
            } catch (Exception e) {
                log.warn("[TIANGGE] {} attempt {} failed: {}", actionDesc, attempt, e.getMessage());
                if (attempt == maxAttempts) {
                    log.error("[TIANGGE] {} failed after {} attempts", actionDesc, maxAttempts);
                }
                try { Thread.sleep(1000L * attempt); } catch (InterruptedException ignored) {}
            }
        }
    }

    @Override
    public void reportDecision(String tianggeOrderId, String decision, String shopOrderId, String reason) {
        try {
            var requestObj = new JsonOrderDecisionRequest(decision, shopOrderId, reason);
            String jsonBody = jsonMapper.writeValueAsString(requestObj);

            HttpRequest request = createRequestBuilder("/orders/" + tianggeOrderId + "/decision")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            sendWithRetry(request, "Order decision for " + tianggeOrderId + " (" + decision + ")");
        } catch (Exception e) {
            log.error("[TIANGGE] Failed to report decision for order {}: {}", tianggeOrderId, e.getMessage());
        }
    }

    @Override
    public void resolveBackorder(String tianggeOrderId, String status) {
        try {
            var requestObj = new JsonBackorderResolutionRequest(status);
            String jsonBody = jsonMapper.writeValueAsString(requestObj);

            HttpRequest request = createRequestBuilder("/orders/" + tianggeOrderId + "/resolution")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            sendWithRetry(request, "Backorder resolution for " + tianggeOrderId + " (" + status + ")");
        } catch (Exception e) {
            log.error("[TIANGGE] Failed to resolve backorder for order {}: {}", tianggeOrderId, e.getMessage());
        }
    }

    @Override
    public void confirmCancellation(String tianggeOrderId, boolean restocked) {
        try {
            var requestObj = new JsonCancellationConfirmRequest(restocked);
            String jsonBody = jsonMapper.writeValueAsString(requestObj);

            HttpRequest request = createRequestBuilder("/orders/" + tianggeOrderId + "/cancellation")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            sendWithRetry(request, "Cancellation confirm for " + tianggeOrderId);
        } catch (Exception e) {
            log.error("[TIANGGE] Failed to confirm cancellation for order {}: {}", tianggeOrderId, e.getMessage());
        }
    }

    // JSON DTOs
    private record JsonListingRequest(
            @JsonProperty("sellerSku") String sellerSku,
            @JsonProperty("title") String title,
            @JsonProperty("supplierSku") String supplierSku
    ) {}

    private record JsonStockUpdateRequest(
            @JsonProperty("sellerSku") String sellerSku,
            @JsonProperty("available") int available
    ) {}

    private record JsonHeartbeatRequest(
            @JsonProperty("appName") String appName,
            @JsonProperty("startedAt") String startedAt,
            @JsonProperty("uptimeSeconds") long uptimeSeconds
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record JsonOrderDecisionRequest(
            @JsonProperty("decision") String decision,
            @JsonProperty("shopOrderId") String shopOrderId,
            @JsonProperty("reason") String reason
    ) {}

    private record JsonBackorderResolutionRequest(
            @JsonProperty("status") String status
    ) {}

    private record JsonCancellationConfirmRequest(
            @JsonProperty("restocked") boolean restocked
    ) {}
}

