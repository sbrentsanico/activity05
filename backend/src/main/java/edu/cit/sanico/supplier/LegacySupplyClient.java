package edu.cit.sanico.supplier;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
class LegacySupplyClient implements LegacySupplyApi {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyClient.class);

    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final LegacySupplySessionManager sessionManager;
    private final HttpClient httpClient;
    private final XmlMapper xmlMapper;

    LegacySupplyClient(
        @Value("${legacysupply.base-url:https://legacysupply.onrender.com/api/v1}") String baseUrl,
        @Value("${legacysupply.client-id:}") String clientId,
        @Value("${legacysupply.api-key:}") String apiKey,
        LegacySupplySessionManager sessionManager
    ) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.sessionManager = sessionManager;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
        this.xmlMapper = new XmlMapper();
    }

    synchronized void ensureAuthenticated() {
        if (sessionManager.hasValidSession()) {
            return;
        }
        authenticate();
    }

    private void authenticate() {
        if (clientId == null || clientId.isBlank() || apiKey == null || apiKey.isBlank()) {
            throw new SupplierException("LegacySupply credentials missing: LS_CLIENT_ID or LS_API_KEY is not configured.");
        }

        try {
            XmlAuthRequest authReq = new XmlAuthRequest(clientId, apiKey);
            String xmlBody = xmlMapper.writeValueAsString(authReq);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/token"))
                .header("Content-Type", "application/xml; charset=utf-8")
                .header("Accept", "application/xml")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(xmlBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                XmlAuthResponse authResponse = xmlMapper.readValue(response.body(), XmlAuthResponse.class);
                sessionManager.setSessionToken(authResponse.sessionToken(), authResponse.issuedAt());
                log.info("[LegacySupply] Successfully authenticated for client {}", clientId);
            } else {
                String errMsg = extractErrorMessage(response.body(), response.statusCode());
                log.error("[LegacySupply] Authentication failed (HTTP {}): {}", response.statusCode(), errMsg);
                throw new SupplierException("LegacySupply authentication failed: " + errMsg);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new SupplierException("Failed to connect to LegacySupply during authentication", e);
        }
    }

    @Override
    public XmlCatalog fetchCatalog() {
        return executeWithRetry(() -> {
            String token = getSessionToken();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/catalog"))
                .header("X-LS-Session", token)
                .header("Accept", "application/xml")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            handleSessionExpiry(response);

            if (response.statusCode() == 200) {
                return xmlMapper.readValue(response.body(), XmlCatalog.class);
            }
            throw buildHttpException(response);
        }, "fetchCatalog");
    }

    @Override
    public XmlPurchaseOrderAck placeOrder(XmlPurchaseOrder order, String requestId) {
        return executeWithRetry(() -> {
            String token = getSessionToken();
            String xmlBody = xmlMapper.writeValueAsString(order);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/purchase-orders"))
                .header("Content-Type", "application/xml; charset=utf-8")
                .header("Accept", "application/xml")
                .header("X-LS-Session", token)
                .timeout(Duration.ofSeconds(25))
                .POST(HttpRequest.BodyPublishers.ofString(xmlBody));

            if (requestId != null && !requestId.isBlank()) {
                reqBuilder.header("X-Request-Id", requestId);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            handleSessionExpiry(response);

            if (response.statusCode() == 201 || response.statusCode() == 200) {
                return xmlMapper.readValue(response.body(), XmlPurchaseOrderAck.class);
            }
            throw buildHttpException(response);
        }, "placeOrder:" + order.supplierSku());
    }

    @Override
    public XmlPurchaseOrderStatus getOrderStatus(String poNumber) {
        return executeWithRetry(() -> {
            String token = getSessionToken();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/purchase-orders/" + poNumber))
                .header("X-LS-Session", token)
                .header("Accept", "application/xml")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            handleSessionExpiry(response);

            if (response.statusCode() == 200) {
                return xmlMapper.readValue(response.body(), XmlPurchaseOrderStatus.class);
            }
            throw buildHttpException(response);
        }, "getOrderStatus:" + poNumber);
    }

    private String getSessionToken() {
        ensureAuthenticated();
        return sessionManager.getSessionToken();
    }

    private void handleSessionExpiry(HttpResponse<String> response) {
        if (response.statusCode() == 401) {
            log.warn("[LegacySupply] 401 received from server. Invalidating session.");
            sessionManager.invalidateSession();
        }
    }

    private SupplierException buildHttpException(HttpResponse<String> response) {
        String msg = extractErrorMessage(response.body(), response.statusCode());
        return new SupplierException("LegacySupply API error (HTTP " + response.statusCode() + "): " + msg);
    }

    private String extractErrorMessage(String body, int httpStatus) {
        if (body != null && body.contains("<LSError>")) {
            try {
                XmlError err = xmlMapper.readValue(body, XmlError.class);
                return err.code() + " - " + err.message();
            } catch (Exception ignored) {}
        }
        return "HTTP " + httpStatus + " " + (body != null && body.length() < 200 ? body : "");
    }

    @FunctionalInterface
    interface SupplierRequest<T> {
        T execute() throws Exception;
    }

    private <T> T executeWithRetry(SupplierRequest<T> request, String actionName) {
        int maxAttempts = 5;
        long backoffMs = 2000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return request.execute();
            } catch (SupplierException se) {
                // If it was a 401 session expiry, retry with fresh authentication
                if (se.getMessage().contains("HTTP 401") || se.getMessage().contains("E-AUTH")) {
                    log.warn("[LegacySupply] Retrying {} after session expiry (attempt {}/{})", actionName, attempt, maxAttempts);
                    sessionManager.invalidateSession();
                    ensureAuthenticated();
                    if (attempt == maxAttempts) throw se;
                    continue;
                }

                // If 503 outage or 429 rate limit, apply exponential backoff and retry safely
                boolean isOutageOrRate = se.getMessage().contains("HTTP 503") ||
                                         se.getMessage().contains("HTTP 429") ||
                                         se.getMessage().contains("E-SYS") ||
                                         se.getMessage().contains("E-RATE");

                if (isOutageOrRate && attempt < maxAttempts) {
                    log.warn("[LegacySupply] Transient error during {} (attempt {}/{}): {}. Backing off {}ms...",
                        actionName, attempt, maxAttempts, se.getMessage(), backoffMs);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw se;
                    }
                    backoffMs = Math.min(backoffMs * 2, 8000);
                    continue;
                }
                throw se;
            } catch (Exception e) {
                if (attempt < maxAttempts) {
                    log.warn("[LegacySupply] Network exception during {} (attempt {}/{}): {}. Retrying in {}ms...",
                        actionName, attempt, maxAttempts, e.getMessage(), backoffMs);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SupplierException("Request interrupted: " + actionName, ie);
                    }
                    backoffMs = Math.min(backoffMs * 2, 8000);
                    continue;
                }
                throw new SupplierException("Failed executing " + actionName + " after " + maxAttempts + " attempts", e);
            }
        }
        throw new SupplierException("Failed executing " + actionName + " after max retries");
    }
}
