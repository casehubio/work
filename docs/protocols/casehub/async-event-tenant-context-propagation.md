---
id: PP-20260609-fb6563
title: "@ObservesAsync handlers must establish tenant context via TenantContextRunner"
type: rule
scope: repo
applies_to: "Any @ObservesAsync CDI event handler that accesses tenant-scoped stores"
severity: critical
refs:
  - runtime/src/main/java/io/casehub/work/runtime/service/TenantContextRunner.java
violation_hint: "Store query in @ObservesAsync handler returns data from wrong tenant or fails with null tenancyId"
garden_ref: "GE-20260609-a23a8b"
created: 2026-06-09
---

`@ObservesAsync` fires outside the originating request scope — no CDI request context or `CurrentPrincipal` is available. Extract `tenancyId` from the event source (e.g. `WorkItem.getTenancyId()`), then wrap the callback in `tenantContextRunner.runInTenantContext(tenancyId, () -> ...)`. Without this, store queries execute unscoped and return data from all tenants or fail with a null tenancyId.
