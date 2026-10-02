package edu.cit.sanico.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_tiangge_orders")
class ProcessedTianggeOrder {

    @Id
    @Column(name = "tiangge_order_id")
    private String tianggeOrderId;

    @Column(name = "local_order_id")
    private UUID localOrderId;

    @Column(name = "decision")
    private String decision;

    @Column(name = "processed_at")
    private Instant processedAt;

    public ProcessedTianggeOrder() {}

    public ProcessedTianggeOrder(String tianggeOrderId, UUID localOrderId, String decision) {
        this.tianggeOrderId = tianggeOrderId;
        this.localOrderId = localOrderId;
        this.decision = decision;
        this.processedAt = Instant.now();
    }

    public String getTianggeOrderId() { return tianggeOrderId; }
    public void setTianggeOrderId(String tianggeOrderId) { this.tianggeOrderId = tianggeOrderId; }

    public UUID getLocalOrderId() { return localOrderId; }
    public void setLocalOrderId(UUID localOrderId) { this.localOrderId = localOrderId; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
