package io.casehub.work.ledger.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.work.ledger.model.WorkItemLedgerEntry;

/**
 * Typed repository for {@link WorkItemLedgerEntry} records.
 *
 * <p>
 * Extends {@link LedgerEntryRepository} with WorkItem-specific query methods that
 * return concrete {@link WorkItemLedgerEntry} instances rather than the base type.
 * Alias methods ({@code findByWorkItemId}, {@code findLatestByWorkItemId}) delegate to the
 * base {@code findBySubjectId} / {@code findLatestBySubjectId} with typed results.
 *
 * <p>All queries are tenant-scoped. The implementation resolves the tenant from
 * {@link io.casehub.platform.api.identity.CurrentPrincipal#tenancyId()}.
 */
public interface WorkItemLedgerEntryRepository extends LedgerEntryRepository {

    /**
     * Return all ledger entries for the given WorkItem in ascending sequence order.
     *
     * @param workItemId the WorkItem UUID
     * @return ordered list of typed entries; empty if none exist
     */
    List<WorkItemLedgerEntry> findByWorkItemId(UUID workItemId);

    /**
     * Return the most recent ledger entry for the given WorkItem, or empty if none.
     *
     * @param workItemId the WorkItem UUID
     * @return the latest typed entry, or empty if no entries exist
     */
    Optional<WorkItemLedgerEntry> findLatestByWorkItemId(UUID workItemId);

    /**
     * Return the earliest ledger entry for the given WorkItem (sequenceNumber = 1), or empty if none.
     *
     * <p>
     * The first entry for any WorkItem is always the CREATED entry. Used when wiring
     * causal chains from a parent SPAWNED entry back to each child's CREATED entry.
     *
     * @param workItemId the WorkItem UUID
     * @return the earliest typed entry, or empty if no entries exist
     */
    Optional<WorkItemLedgerEntry> findEarliestByWorkItemId(UUID workItemId);
}
