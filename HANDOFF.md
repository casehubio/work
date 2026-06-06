# HANDOFF — 2026-06-06

## Last Session

Implementation session for #177 (conditional outcomes) and #199 (PATCH templates). Both went through 4 rounds of spec review before implementation — each round caught real bugs (unsafe ArrayNode cast, wrong return type on `decodePermittedOutcomes`, missing `WorkItemWithAuditResponse` change site, null guard). Implementation: `Outcome` record gained `condition` field; `OutcomeValidator @ApplicationScoped` bean encapsulates JEXL evaluation; `WorkItem.permittedOutcomes` now stores full Outcome objects with format detection for legacy rows; PATCH handler covers 25 template fields with correct `intValue()`/`booleanValue()` semantics. 899 tests green. Branch closed, squashed to 2 commits, pushed to both remotes. ADR-0006 and ADR-0007 written.

Key finding: `WorkItemContextBuilder` is `public final class` with private constructor — NOT CDI despite being in the `event` package. It is a static utility class called statically from `OutcomeValidator`.

## Immediate Next Step

Pick next issue — #191 (extract persistence-memory module, M/Low) or #236 (VocabularyScope → Path hierarchy, M/Low) are both unblocked and clear.

## What's Left

- casehubio/parent#170 — update `docs/repos/casehub-work.md` deep-dive: replace DESIGN.md/ARCHITECTURE.md refs with ARC42STORIES.MD · S · Low
- casehubio/parent#180 — sync casehub-work.md deep-dive for #177/#199 (PATCH endpoint + permittedOutcomes type change) · S · Low
- #252 — pre-existing ledger compile failure: `JpaWorkItemLedgerEntryRepository` missing `findEventsByActorId()` from upstream ledger SPI change · S · Low

## What's Next

| # | Description | Scale | Complexity | Notes |
|---|-------------|-------|------------|-------|
| #191 | Extract persistence-memory module from testing/ | M | Low | |
| #236 | VocabularyScope enum → Path-based scope hierarchy | M | Low | Flyway migration needed |
| #240 | Human task lifecycle alignment (states, engine, spec gaps) | L | High | Design-heavy |
| #237 | Structured progress — schema-validated, hierarchical | L | High | Ideas only |
| #238 | Saga compensation across casehub platform | XL | High | Ideas only |

## References

- Garden: `GE-20260606-e5f0ab` (jvm/) — migrate JSON column format without Flyway via readTree()+isObject() format detection
- Garden: `GE-20260606-1954f5` (jvm/) — mvn test -pl <module> uses installed jars, not reactor output
- Garden: `GE-20260606-527f94` (jvm/) — JEXL silent(true) swallows broken conditions identically to false conditions
- Blog: `2026-06-06-mdp01-reviewing-your-way-to-fewer-surprises.md`
- Protocol: `PP-20260606-ef909e` — snapshot template constraints at instantiation, never re-read at completion
- ADR: `docs/adr/0006-evaluate-outcome-conditions-at-completion-time.md`
- ADR: `docs/adr/0007-jexl-as-outcome-condition-language.md`
- casehubio/parent#170 — casehub-work.md deep-dive still references deleted DESIGN.md
- casehubio/parent#180 — casehub-work.md sync for #177/#199 changes
