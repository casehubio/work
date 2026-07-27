# HANDOFF — 2026-07-27

## Last Session

Wacky Manor POC — built Phase 1 (game engine) and Phase 2 (UI) end-to-end. 18 commits on `feat/wacky-manor-poc` in casehubio/examples. Repo: `examples/wacky-manor/`. Phase 1: WorldState, ActionResolver, TriggerEvaluator, SceneDirector, CharacterAgentLoop (virtual threads), ScenarioOrchestrator, 4-channel Qhorus normative layout. 113 tests. Live LLM scenario passed — tea poisoning scene with cartoon comedy. Phase 2: Lit 3 frontend with SVG character illustrations, room chat panels, narrator panel, WebSocket real-time events.

Key design insight: the original spec over-scripted the plot (triggers and scenes drive events TO characters). Restructured roadmap with Phase 2.5 to validate autonomous characters before scaling.

## Immediate Next Step

Phase 2.5 — autonomous character validation. Same 3 rooms, 5 characters. Add goals to observation template, disable scripted triggers. Verdict gate: does the Hooded Claw discover poison and scheme on his own?

## What's Left

- casehubio/garden#2 — extend persistence-backend-cdi-priority.md with Tier 3 · S · Low

## What's Next

| Phase | Description | Scale | Complexity | Notes |
|---|---|---|---|---|
| 2.5 | Autonomous character validation — goals in observation, no scripts | M | Med | Critical — validates core premise |
| 2.6 | ObservationAccumulator — casehub-blocks tiered batching | S | Med | Needed before more rooms/characters |
| 2.7 | Live LLM narrator — wire NarratorAgent | S | Low | NarratorAgent class exists, not wired |
| 2.8 | NPC system — Tier 2/3 scripted fixtures, player/NPC split | M | Med | RPG framing |
| 2.9 | Scale to 6 rooms — Library, Laboratory, Tower | L | Med | After 2.5 validates |
| 3.0 | Platform integration — memory, trust, HiL, replay | XL | High | Full casehub exercise |

## References

- POC-SPEC: `examples/wacky-manor/docs/POC-SPEC.md`
- VISION: `examples/wacky-manor/docs/VISION.md`
- Plan: `examples/wacky-manor/docs/plans/2026-07-26-phase1-game-engine.md`
- Memory: `project_wacky-manor.md` — has full phase breakdown and run instructions
