# HANDOFF — 2026-08-13

## Last Session

Closed branch `issue-333-progress-api-docs-spi-fix` (covers #333, #337, #340). WorkItemStore SPI extraction complete — `WorkItem` is now an immutable record in `api/`, `WorkItemEntity` (JPA) stays in `runtime/`. All modules compile including test sources. Progress REST API documented (18 endpoints). Six peer repos (aml, clinical, engine, iot, devtown, life) updated with entity rename + import fixes and pushed to both origin and upstream.

### What's Done

- **#337 (SPI extraction):** WorkItemStore, CrossTenantWorkItemStore, WorkItemQuery, WorkItemRootView, WorkItemSummaryBuilder, LabelPatternMatcher moved to `api/`. WorkItem record (46 fields + builder + toBuilder). WorkItemEntityMapper for bidirectional conversion. persistence-memory depends on api/ only. 234 files changed across all modules.
- **#333 (progress docs):** `docs/api-reference.md` updated with full Progress section.
- **#340 (resolvedScope):** Already fixed — closed.
- **Peer repos pushed:** aml, clinical, engine, iot, devtown → both remotes. life → origin only (upstream has 18-commit divergence with Flyway migration conflicts).

## Immediate Next Step

Run `/work` to pick next issue. The life repo upstream push needs its own session to resolve Flyway migration rebase conflicts.

## Cross-Module

**Blocking** (work SNAPSHOT not yet published):
- aml, clinical, engine, iot, devtown, life — depend on new work SNAPSHOT with WorkItemStore at `api.spi` package. Publish SNAPSHOT before those repos can build against the new imports.

## References

| Artifact | Path |
|----------|------|
| Design spec | `specs/issue-333-progress-api-docs-spi-fix/2026-08-13-progress-docs-spi-extraction-design.md` |
| Plan | `plans/2026-08-13-progress-docs-spi-extraction.md` |
| API reference | `docs/api-reference.md` (Progress section) |
| Forage entry | `~/.claude/garden/entries/GE-20260813-intellij-rename-collateral.md` |
