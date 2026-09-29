# io.casehub.work.api.LedgerEntryIdSetter

**Package:** `io.casehub.work.api`

**Kind:** `interface`

SPI for setting the ledger entry ID on a `WorkItemLifecycleEvent` after
the ledger entry has been persisted.

<p>Only `WorkItemLifecycleEvent.ledgerEntryIdSetter()` provides an instance.
The setter accesses the event's private field, keeping the public API immutable.

## Methods

### `public abstract void set(io.casehub.work.api.WorkItemLifecycleEvent event, java.util.UUID ledgerEntryId)`

#### Parameters

- `event` (`io.casehub.work.api.WorkItemLifecycleEvent`)
- `ledgerEntryId` (`java.util.UUID`)
