# HANDOFF — 2026-07-29

## Last Session

Wacky Manor POC — built Phase 1 (game engine) and Phase 2 (UI) end-to-end. Phase 2.5 validated autonomous characters with affordance grounding. Separately, eidos#107 (Jungian personality framework) delivered — weighted cognitive function profiles, MBTI derivation, personality evolution. Mapped eidos#107 into the Wacky Manor roadmap as Phase 2.5b.

## Immediate Next Step

File the AffordanceRenderer issue in casehub-blocks (draft at `wacky-manor/docs/issues/blocks-affordance-renderer.md` — needs `gh auth login` first). Then Phase 2.5b: swap flat dispositions for Jungian profiles on all 5 characters and A/B compare against the 2.5 baseline.

## What's Left

- casehubio/garden#2 — extend persistence-backend-cdi-priority.md with Tier 3 · S · Low

## What's Next

| Phase | Description | Scale | Complexity | Notes |
|---|---|---|---|---|
| 2.5b | Jungian personality profiles — `MbtiTypeTerm` profiles on characters, A/B vs 2.5 baseline | S | Med | eidos#107 delivered; `dispositionProfile` field ready |
| 2.6 | ObservationAccumulator — casehub-blocks tiered batching + AffordanceRenderer | S | Med | Needs AffordanceRenderer issue filed first |
| 2.7 | Live LLM narrator — wire NarratorAgent | S | Low | NarratorAgent class exists, not wired |
| 2.8 | NPC system — Tier 2/3 scripted fixtures, player/NPC split | M | Med | RPG framing |
| 2.9 | Scale to 6 rooms — Library, Laboratory, Tower | L | Med | After 2.5b validates |
| 3.0 | Platform integration — memory, trust, HiL, replay, personality evolution | XL | High | Dynamic DispositionSignalStore + drift detection |

## References

- POC-SPEC: `examples/wacky-manor/docs/POC-SPEC.md`
- VISION: `examples/wacky-manor/docs/VISION.md`
- Plan: `examples/wacky-manor/docs/plans/2026-07-26-phase1-game-engine.md`
- Memory: `project_wacky-manor.md` — has full phase breakdown and run instructions
