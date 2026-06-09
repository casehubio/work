# CaseHub Protocols — casehub-work

Platform-specific rules for the casehub-work module.

| File | Rule Summary | Applies To |
|------|-------------|------------|
| [async-event-tenant-context-propagation.md](async-event-tenant-context-propagation.md) | @ObservesAsync handlers must establish tenant context via TenantContextRunner | Any @ObservesAsync handler accessing tenant-scoped stores |
| [store-tenancy-stamping-on-insert.md](store-tenancy-stamping-on-insert.md) | Store put() stamps tenancyId on insert, preserves on update | All tenant-scoped store put() implementations |
| [cross-tenant-store-minimal-surface.md](cross-tenant-store-minimal-surface.md) | @CrossTenant stores expose only use-case-specific methods | All @CrossTenant-qualified store interfaces |
