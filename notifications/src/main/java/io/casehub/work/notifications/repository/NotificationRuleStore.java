package io.casehub.work.notifications.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.casehub.work.notifications.model.WorkItemNotificationRule;

/**
 * Store SPI for {@link WorkItemNotificationRule} persistence.
 *
 * <p>All queries are scoped to the current tenant via the implementation's
 * {@link io.casehub.platform.api.identity.CurrentPrincipal}.
 *
 * <p>
 * <strong>CDI backend activation:</strong><br>
 * Tier 1: {@code @ApplicationScoped} (JPA/SQL, default) — {@code casehub-work-notifications}.<br>
 * Tier 3: {@code @Alternative @Priority(100)} (in-memory, ephemeral) — {@code casehub-work-persistence-memory}.<br>
 * No Tier 2 (MongoDB) exists yet.
 */
public interface NotificationRuleStore {

    /**
     * Persist or update a notification rule and return the saved instance.
     * Stamps {@code tenancyId} from the current principal on insert when null.
     *
     * @param rule the notification rule to persist; must not be {@code null}
     * @return the persisted rule
     */
    WorkItemNotificationRule put(WorkItemNotificationRule rule);

    /**
     * Retrieve a notification rule by its primary key, scoped to the current tenant.
     *
     * @param id the UUID primary key
     * @return an {@link Optional} containing the rule, or empty if not found
     */
    Optional<WorkItemNotificationRule> get(UUID id);

    /**
     * Return all enabled rules that match the given event type, scoped to the current tenant.
     *
     * @param eventType the event type name (e.g. {@code "ASSIGNED"})
     * @return list of matching enabled rules; never null
     */
    List<WorkItemNotificationRule> findEnabledForEventType(String eventType);

    /**
     * Return all enabled rules for the current tenant, ordered by creation time ascending.
     *
     * @return list of enabled rules; never null
     */
    List<WorkItemNotificationRule> findAllEnabled();

    /**
     * Return all rules for the current tenant.
     *
     * @return unordered list of all rules; never null
     */
    List<WorkItemNotificationRule> scanAll();

    /**
     * Delete a notification rule by its primary key, scoped to the current tenant.
     *
     * @param id the UUID primary key
     * @return {@code true} if the entity was deleted, {@code false} if not found
     */
    boolean delete(UUID id);
}
