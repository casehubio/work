# HANDOFF — 2026-06-04

## Last Session

ARC42STORIES.MD migration (#246). Created `ARC42STORIES.MD` at project root — 1861 lines, 35 chapters, 7 layer entries, §1–§13 complete. Deleted `docs/DESIGN.md` and `docs/ARCHITECTURE.md`. Updated CLAUDE.md in 3 places. Closed #246. Document written code-first: all Key file paths and CDI annotations verified against production source before writing. Three-check quality gate passed (issue refs, file existence, CDI annotations). Branch closed via work-end.

Key finding: DESIGN.md tracked 27 numbered phases; the actual codebase had 35 chapters. Eight phases from the 2026-04-20 session (WorkItemTemplate, WorkItemNote, WorkItemRelation, SSE, recurring schedules, Micrometer metrics, bulk ops) plus ClaimSlaPolicy, MongoDB, Issue Tracker, Round-Robin, and LLM Assist features were missing from the plan log entirely.

## Immediate Next Step

Pick next issue from the backlog — `#191` (extract persistence-memory module) or `#236` (VocabularyScope → Path hierarchy) are the obvious next candidates.

## What's Left

- Stale open branches (no EPIC-CLOSED.md, no recent commits): `epic-excluded-users`, `epic-exclusion-audit`, `epic-output-schema` (11 days, old naming), `issue-228-branch-hygiene`, `issue-233-xs-s-batch-cleanup` (5 days) — review and close or continue
- casehubio/parent#170 — update `docs/repos/casehub-work.md` deep-dive to reference ARC42STORIES.MD instead of DESIGN.md/ARCHITECTURE.md (filed this session, not yet done) · S · Low

## What's Next

| # | Description | Scale | Complexity | Notes |
|---|-------------|-------|------------|-------|
| #191 | feat: extract persistence-memory module from testing/ | M | Low | |
| #236 | feat: replace VocabularyScope enum with Path-based scope hierarchy | M | Low | |
| #240 | design: human task lifecycle alignment | L | High | |
| #237 | idea: structured progress (schema-validated, hierarchical) | L | High | ideas only |
| #238 | idea: saga compensation across casehub platform | XL | High | ideas only |

## References

- Garden: `GE-20260604-a6f008` (jvm/) — ARC42STORIES.MD template module ownership assumptions wrong
- Garden: `GE-20260604-55a371` (tools/) — git rebase fails after filter-repo prunes base commit; use cherry-pick instead
- Protocol: `PP-20260604-1ff5b9` — foundation-tier ARC42STORIES.MD defines its own layer taxonomy
- Blog: `2026-06-04-mdp16-35-chapters-design-md.md` — the 35 chapters DESIGN.md didn't know about
- casehubio/parent#170 — downstream update: casehub-work.md deep-dive references DESIGN.md/ARCHITECTURE.md (stale)
