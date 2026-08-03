# HANDOFF — 2026-08-03

## Last Session

Phase 2.7 — live LLM narrator. Built NarratorAgent (stateful class with SummarisationRunner from casehub-blocks), NarratorSummariser (Summariser SPI), ManorEventDispatcher (centralized event fan-out), and wired into ScenarioOrchestrator for AUTONOMOUS mode. Design review (4 dimensions, 45 issues, $37.46) drove ManorEventDispatcher extraction and AUTONOMOUS-only mode. Also shipped blocks#82 epic (7 issues — Compactor SPI, failure recovery, atomic drain, flush(), etc.) and blocks#90 (flush) as narrator prerequisites.

## Immediate Next Step

Run the verdict gate: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl wacky-manor -Pllm-eval -Dtest=NarratorIntegrationTest` — confirms narrator generates entertaining commentary, not a dry event log.

## What's Left

- Verdict gate not yet run with live LLM — integration test exists but needs `-Pllm-eval` run · XS · Low
- WorldState.addEvent() thread safety — pre-existing gap, needs CopyOnWriteArrayList or synchronization (file issue) · S · Low
- Stashed slot-59 changes — `git stash pop` on worktree 59 to restore issue-2 descriptor/test changes · XS · Low
- blocks#76 spec + blog entry — deferred from prior session · S · Low
- Push garden entry GE-20260728-f7ad43 — committed locally, pre-push hook blocked · XS · Low

## What's Next

| Phase | Description | Scale | Complexity | Notes |
|---|---|---|---|---|
| 2.8 | NPC system — Tier 2/3 scripted fixtures, player/NPC split | M | Med | RPG framing |
| 2.9 | Scale to 6 rooms — Library, Laboratory, Tower (#6) | L | Med | Re-evaluate buffer growth |
| 3.0 | Platform integration — memory, trust, HiL, replay | XL | High | Full casehub exercise |

## Cross-repo Changes This Session

| Repo | What | Branch |
|------|------|--------|
| casehubio/blocks | Epic #82 — Compactor SPI, failure recovery, atomic drain, WindowPolicy factories, EventStreamBus rename, docs (slot 71, merged) | main |
| casehubio/blocks | #90 — SummarisationRunner.flush() | main |
| casehubio/examples | Phase 2.7 narrator wiring | feat/wacky-manor-poc (pushed to fork) |
| mdproctor.github.io | Blog: "When the Narrator Finds Its Voice" | _notes (published) |

## Key Decisions

- Narrator uses direct collection via SummarisationRunner, not PartitionedObservationService (omniscient vs room-scoped)
- AUTONOMOUS mode only — SCRIPTED mode keeps trigger-fired narration
- ManorEventDispatcher centralizes all event fan-out (emerged from design review)
- Hybrid trigger: WindowPolicy.of(15_000, 5) — 5 events OR 15 seconds
- MechanicalCompactor implements blocks Compactor<ManorEvent> SPI

## References

- Spec: `specs/issue-9-narrator-wiring/2026-08-02-narrator-wiring-design.md`
- Blog: `~/claude/mdproctor.github.io/_notes/2026-08-03-mdp01-when-the-narrator-finds-its-voice.md`
- Design review: `~/reviews/casehub-worktrees/narrator-wiring-*` (4 dimensions)
- Plan: `plans/2026-08-03-narrator-wiring.md`
- blocks epic: casehubio/blocks#82 (7 issues, all closed)
- blocks flush: casehubio/blocks#90 (closed)
- Deferred issues: casehubio/examples#6 (buffer growth), #7 (per-type EventLevels), #8 (full blocks pipeline)
