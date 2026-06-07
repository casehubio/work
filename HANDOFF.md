# HANDOFF — 2026-06-07

## Last Session

Implemented #191 (persistence-memory module extraction) end-to-end: spec (two review rounds), implementation plan, subagent-driven execution. `testing/` → `persistence-memory/` via git mv. Five stores re-annotated `@Alternative @Priority(100)` (new Tier 3 in CDI ladder — priority inversion: least-capable backend, highest priority). All stores made thread-safe with ConcurrentHashMap; AuditEntryStore restructured to per-workItemId keyed concurrent map. Also fixed pre-existing #252 (ledger compile failure from upstream SPI change — `findScore` → `findByActorId` in 3 files). CI green.

## Immediate Next Step

Pick next issue — #236 (VocabularyScope → Path hierarchy, M/Low, Flyway migration) or #254 (ephemeral deployment integration test, S/Med) are both unblocked.

## What's Left

- casehubio/parent#170 — update `docs/repos/casehub-work.md` deep-dive: replace DESIGN.md/ARCHITECTURE.md refs with ARC42STORIES.MD · S · Low
- casehubio/parent#195 — sync casehub-work.md deep-dive for #191 (testing → persistence-memory) · S · Low
- casehubio/garden#2 — extend persistence-backend-cdi-priority.md with Tier 3 (@Priority(100)) · S · Low
- #255 — minor review findings: Javadoc consistency, findAll gap on AuditEntryStore · XS · Low

## What's Next

| # | Description | Scale | Complexity | Notes |
|---|-------------|-------|------------|-------|
| #236 | VocabularyScope enum → Path-based scope hierarchy | M | Low | Flyway migration needed |
| #254 | Ephemeral deployment integration test — verify datasource.active=false | S | Med | Validates persistence-memory README guidance |
| #253 | MongoDB completeness — 3 missing stores (Note, IssueLink, RoutingCursor) | M | Low | |
| #240 | Human task lifecycle alignment (states, engine, spec gaps) | L | High | Design-heavy |
| #237 | Structured progress — schema-validated, hierarchical | L | High | Ideas only |
| #238 | Saga compensation across casehub platform | XL | High | Ideas only |

## References

- Garden: `GE-20260607-3ded98` (jvm/) — ConcurrentHashMap.getOrDefault + List.of() type inference failure
- Blog: `2026-06-07-mdp07-the-module-that-was-never-just-for-testing.md`
- Spec: `docs/superpowers/specs/2026-06-06-persistence-memory-module-design.md`
- casehubio/parent#195 — casehub-work.md deep-dive sync for persistence-memory
- casehubio/garden#2 — protocol Tier 3 update
