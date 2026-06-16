---
id: PP-20260616-4896da
title: "Queue filter scope is management metadata — store query methods must not filter by scope"
type: rule
scope: repo
applies_to: "casehub-work queues module — WorkItemFilterStore, QueueViewStore, any future store query"
severity: important
violation_hint: "Adding a scope predicate to findActive() or scanAll() silently changes WHICH WorkItems get filtered (execution semantic), not who can manage filters (management intent). The two concerns must never be conflated."
created: 2026-06-16
---

`WorkItemFilter.scope` and `QueueView.scope` are management visibility metadata: they govern who can list, create, and delete a filter or queue view in a UI or access-control layer. They are not execution predicates. `findActive()` returns all active tenant filters regardless of scope; `scanAll()` returns all tenant queue views regardless of scope; the filter engine evaluates every active filter against every WorkItem. A future implementor adding a scope predicate to `findActive()` (e.g. to show only "my team's filters") would be changing which WorkItems get filtered — a silent execution semantic change — not implementing access control. Scope enforcement for management visibility belongs above the store layer (REST resource or service), never inside it.
