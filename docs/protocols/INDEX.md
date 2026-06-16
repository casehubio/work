# Protocols — casehub-work

Standing architectural rules and conventions for the casehub-work module.

## CaseHub Platform

See [casehub/INDEX.md](casehub/INDEX.md) for the full listing.

| File | Rule Summary | Applies To |
|------|-------------|------------|
| [async-event-tenant-context-propagation](casehub/async-event-tenant-context-propagation.md) | @ObservesAsync must use TenantContextRunner | Async event handlers |
| [store-tenancy-stamping-on-insert](casehub/store-tenancy-stamping-on-insert.md) | put() stamps tenancyId on insert only | Store implementations |
| [cross-tenant-store-minimal-surface](casehub/cross-tenant-store-minimal-surface.md) | @CrossTenant interfaces bounded by use case | Cross-tenant stores |
| [queue-filter-scope-management-only](casehub/queue-filter-scope-management-only.md) | Filter/queue scope is management metadata — not an execution predicate | queues WorkItemFilterStore, QueueViewStore |
