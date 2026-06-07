package io.casehub.work.runtime.repository;

import java.util.List;
import java.util.UUID;

import io.casehub.work.runtime.model.AuditEntry;

/**
 * Append-only store SPI for {@link AuditEntry} records.
 * Replaces {@code AuditEntryRepository} — aligned with KV store terminology.
 *
 * <p>
 * <strong>CDI backend activation (four-tier priority ladder):</strong><br>
 * Tier 0: {@code @DefaultBean} (no-op fallback) — not applicable to this SPI.<br>
 * Tier 1: {@code @ApplicationScoped} (JPA/SQL, default) — {@code casehub-work} runtime.<br>
 * Tier 2: {@code @Alternative @Priority(1)} (MongoDB) — {@code casehub-work-persistence-mongodb}.<br>
 * Tier 3: {@code @Alternative @Priority(100)} (in-memory, ephemeral) — {@code casehub-work-persistence-memory}.<br>
 * Adding a backend module to the classpath activates it automatically — no consumer changes.
 * See the platform
 * <a href="https://github.com/casehubio/garden/blob/main/docs/protocols/universal/persistence-backend-cdi-priority.md">persistence-backend-cdi-priority</a>
 * protocol.
 */
public interface AuditEntryStore {

    /**
     * Append an audit entry to the store.
     *
     * @param entry the entry to append; must not be {@code null}
     */
    void append(AuditEntry entry);

    /**
     * Return all audit entries for the given WorkItem, in chronological order.
     *
     * @param workItemId the WorkItem primary key
     * @return chronological list of audit entries; may be empty, never null
     */
    List<AuditEntry> findByWorkItemId(UUID workItemId);

    /**
     * Return a paginated page of audit entries matching the given query.
     *
     * @param query filter + pagination parameters; never null
     * @return matching entries for the requested page, ordered by occurredAt DESC
     */
    List<AuditEntry> query(AuditQuery query);

    /**
     * Count total matching audit entries for the given query (ignoring pagination).
     *
     * @param query filter parameters (page/size are ignored)
     * @return total matching entry count
     */
    long count(AuditQuery query);
}
