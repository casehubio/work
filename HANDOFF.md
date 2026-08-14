# HANDOFF — 2026-08-14

## Last Session

Fixed CI — 5 `MongoWorkItemStoreTest` failures in the `persistence-mongodb` module. Two commits landed on main via quick-fix:

**Commit 1** (`b065fbe`) — Closes #354:
- `findById(wi.id())` → `findById(wi.id().toString())` — 3 OCC tests were passing `UUID` to a `String @BsonId` field, triggering `CodecConfigurationException: uuidRepresentation not specified`
- `store.put(wi)` return value captured as `saved` — 2 roundtrip tests were discarding the put result and calling `store.get(wi.id())` with null id

**Commit 2** (`fd4de76`) — Refs #354:
- Added `Long version` to `WorkItem` record — the OCC test (`put_throwsOptimisticLockException_onStaleVersion`) revealed that `MongoWorkItemStore.put()` always read the current version from the database instead of comparing against the caller's expected version
- `MongoWorkItemStore.put()` now uses `stored.version()` for the OCC filter when present (backwards-compatible null fallback)
- Mapped version through `WorkItemEntityMapper.toDomain()`, `MongoWorkItemDocument.toDomain()`, `WorkItemContextBuilder`
- Updated `WorkItemEntityMapperTest.roundTripPreservesAllFields` to account for entity default `version=0L`

CI is GREEN on `fd4de76`.

Also merged branch `issue-510-case-level-sla-timer` (docs-only: design spec and implementation plan for engine issue #510).

## Peer Repo CI Status

| Repo | CI | Issue |
|------|-----|-------|
| work | GREEN | — |

## References

| Artifact | Path |
|----------|------|
| Issue | https://github.com/casehubio/work/issues/354 |
| MongoWorkItemStore | `persistence-mongodb/src/main/java/io/casehub/work/mongodb/MongoWorkItemStore.java` |
| MongoWorkItemStoreTest | `persistence-mongodb/src/test/java/io/casehub/work/mongodb/MongoWorkItemStoreTest.java` |
| WorkItem record | `api/src/main/java/io/casehub/work/api/WorkItem.java` |
