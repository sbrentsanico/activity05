package edu.cit.sanico.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
class LegacySupplySessionManager {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplySessionManager.class);

    private String sessionToken;
    private Instant issuedAt;

    synchronized String getSessionToken() {
        return sessionToken;
    }

    synchronized void setSessionToken(String token, String issuedAtStr) {
        this.sessionToken = token;
        this.issuedAt = parseInstant(issuedAtStr);
        log.info("[LegacySupply] Authenticated session token: {} (issued at: {})", token, this.issuedAt);
    }

    synchronized void invalidateSession() {
        if (sessionToken != null) {
            Duration activeDuration = issuedAt != null ? Duration.between(issuedAt, Instant.now()) : Duration.ZERO;
            log.warn("[LegacySupply] Session token invalidated after {}s (token={})", activeDuration.toSeconds(), sessionToken);
        }
        this.sessionToken = null;
    }

    synchronized boolean hasValidSession() {
        return sessionToken != null && !sessionToken.isBlank();
    }

    private Instant parseInstant(String ts) {
        if (ts == null || ts.isBlank()) return Instant.now();
        try {
            return Instant.parse(ts);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
