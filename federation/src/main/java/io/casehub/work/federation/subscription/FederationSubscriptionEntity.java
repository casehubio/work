package io.casehub.work.federation.subscription;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "federation_subscription")
public class FederationSubscriptionEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "peer_id", nullable = false)
    public String peerId;

    @Column(name = "callback_url", nullable = false, length = 1024)
    public String callbackUrl;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "filter_json", nullable = false, columnDefinition = "TEXT")
    public String filterJson;

    @Column(name = "capabilities_json", columnDefinition = "TEXT")
    public String capabilitiesJson;

    @Column(name = "hmac_secret_encrypted", nullable = false, columnDefinition = "BYTEA")
    public byte[] hmacSecretEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    public SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "consecutive_failures", nullable = false)
    public int consecutiveFailures;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    public enum SubscriptionStatus {
        ACTIVE, SUSPENDED, DEREGISTERED
    }
}
