# Design Journal — issue-177-outcomes-and-patch

### 2026-06-05 · §9.4·casehub-work · Conditional Outcomes

The `Outcome` record (API module) gained an optional `condition` field — a JEXL expression evaluated at completion or rejection time. Conditions are snapshotted into `WorkItem.permittedOutcomes` at instantiation time alongside the outcome name and displayName, consistent with how `inputDataSchema` and `outputDataSchema` are already snapshotted from the template. This means template condition changes do not retroactively affect in-flight WorkItems.

Evaluation is handled by a new `OutcomeValidator @ApplicationScoped` bean in `runtime/service/`, keeping lifecycle transition logic in `WorkItemService` focused on state management. The JEXL context mirrors the filter-rule context (`workItem.*` map, plus `resolution`, `reason`, and `actorId` threaded from the call site). System paths (`completeFromSystem`, `rejectFromSystem`) intentionally bypass condition evaluation, consistent with their existing bypass of outcome-name and schema validation.

`WorkItem.permittedOutcomes` now stores full `Outcome` objects as JSON rather than a flat string array. Legacy string-array rows are decoded via token-level format detection (`readTree()` + `isObject()`) — no Flyway migration needed. Both `WorkItemResponse` and `WorkItemWithAuditResponse` expose `List<Outcome>` (API break, acknowledged; no external consumers).

### 2026-06-05 · §9.4·casehub-work · PATCH /workitem-templates/{id}

Added `PATCH /workitem-templates/{id}` with `Content-Type: application/merge-patch+json` (RFC 7396). Absent fields are left unchanged; null clears. The handler applies 25 template fields across three type patterns — `asText()` for strings, `intValue()` for integers (not `asInt()`, which returns 0 on null nodes), `booleanValue()` for booleans — with explicit special handling for `name` (null → 400), `priority` (enum parse in try-catch), `outcomes` (deserialized as `List<Outcome>`, encoded), and `inputDataSchema`/`outputDataSchema` (isObject check). `createdBy` is silently ignored — authorship is immutable, consistent with how PUT omits it from `UpdateTemplateRequest`.
