---
id: PP-20260609-2144e0
title: "@CrossTenant store interfaces expose only use-case-specific methods"
type: rule
scope: repo
applies_to: "All @CrossTenant-qualified store interfaces"
severity: important
violation_hint: "Cross-tenant store interface with generic put(), delete(), or scanAll() — surface area too broad"
created: 2026-06-09
---

Cross-tenant store interfaces must expose only the methods the specific use case requires. No generic `put()`, `delete()`, or `scanAll()`. Each interface is bounded by its use case (e.g. `CrossTenantWorkItemStore` exposes `findActiveWithDeadlines()` only). This prevents accidental broad data access through the cross-tenant path and makes security review tractable — every cross-tenant method is a named, auditable operation.
