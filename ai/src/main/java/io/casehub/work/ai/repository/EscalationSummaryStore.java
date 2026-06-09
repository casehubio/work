package io.casehub.work.ai.repository;

import java.util.List;
import java.util.UUID;

import io.casehub.work.ai.escalation.EscalationSummary;

/**
 * Store SPI for {@link EscalationSummary} persistence.
 *
 * <p>All queries are scoped to the current tenant via the implementation's
 * {@link io.casehub.platform.api.identity.CurrentPrincipal}.
 *
 * <p>
 * <strong>CDI backend activation:</strong><br>
 * Tier 1: {@code @ApplicationScoped} (JPA/SQL, default) — {@code casehub-work-ai}.<br>
 * Tier 3: {@code @Alternative @Priority(100)} (in-memory, ephemeral) — {@code casehub-work-persistence-memory}.<br>
 * No Tier 2 (MongoDB) exists yet.
 */
public interface EscalationSummaryStore {

    /**
     * Persist an escalation summary and return the saved instance.
     * Stamps {@code tenancyId} from the current principal on insert when null.
     *
     * @param summary the summary to persist; must not be {@code null}
     * @return the persisted summary
     */
    EscalationSummary put(EscalationSummary summary);

    /**
     * Return all escalation summaries for a WorkItem, most recent first,
     * scoped to the current tenant.
     *
     * @param workItemId the WorkItem UUID
     * @return list of summaries ordered by generatedAt descending; never null
     */
    List<EscalationSummary> findByWorkItemId(UUID workItemId);
}
