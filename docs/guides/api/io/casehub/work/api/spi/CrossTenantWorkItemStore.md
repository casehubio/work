# io.casehub.work.api.spi.CrossTenantWorkItemStore

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

Cross-tenant `WorkItem` store for system-level operations.

<p>Unlike the tenant-scoped `WorkItemStore`, this store bypasses
all tenant filtering and returns items from all tenants.  Only inject
this via `@CrossTenant` in system-level services (background jobs,
admin endpoints).

## Methods

### `public abstract java.util.List<io.casehub.work.api.WorkItem> findActiveWithDeadlines()`

Finds all active WorkItems (non-terminal statuses) that have deadlines
(either `expiresAt` or `claimDeadline` is non-null).

<p>Used by timer/expiry jobs to scan across all tenants for items
requiring deadline enforcement.

#### Returns

list of active WorkItems with deadlines, from all tenants
