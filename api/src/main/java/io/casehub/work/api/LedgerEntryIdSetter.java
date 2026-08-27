package io.casehub.work.api;

import java.util.UUID;

/**
 * SPI for setting the ledger entry ID on a {@link WorkItemLifecycleEvent} after
 * the ledger entry has been persisted.
 *
 * <p>Only {@link WorkItemLifecycleEvent#ledgerEntryIdSetter()} provides an instance.
 * The setter accesses the event's private field, keeping the public API immutable.
 */
@FunctionalInterface
public interface LedgerEntryIdSetter {

    void set(WorkItemLifecycleEvent event, UUID ledgerEntryId);
}
