---
id: PP-20260609-bdac7e
title: "Store put() stamps tenancyId on insert, preserves on update"
type: rule
scope: repo
applies_to: "All tenant-scoped store put() implementations"
severity: critical
violation_hint: "Entity saved with null tenancyId, or tenancyId silently changed on update"
created: 2026-06-09
---

On insert (`entity.getTenancyId() == null`), stamp from `CurrentPrincipal.tenancyId()`. On update, preserve the existing value — never overwrite. TenancyId is immutable after entity creation. This prevents both orphaned entities (no tenant) and silent tenant migration (entity moves between tenants on update).
