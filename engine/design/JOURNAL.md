# Design Journal — issue-800-agent-learning-memory

## 2026-08-04 — Architecture design and first implementation

### Decisions

1. **Agent memories reuse CaseMemoryStore** — entityId=agentId, domain-based isolation. No separate store.
2. **Recorder pattern** — AgentExperienceRecorder in engine-runtime, parallel to PersonalitySignalRecorder. Calls ExperienceRecorder SPI (neocortex-memory-api).
3. **Configurable hybrid reflection trigger** — importance threshold + completion count ceiling per CaseDefinition. Async via Vert.x event bus.
4. **GoalLifecycleStore SPI** in eidos-api — separate from AgentDescriptor. Declared goals immutable, discovered goals in store.
5. **ExperienceSignalProvider evolution** — existing provider gains agent-level memory query, no new provider.

### What was built

- Architecture spec: `specs/issue-800-agent-learning-memory/2026-08-04-agent-learning-memory-architecture-design.md`
- Standard design review (4 dimensions, 66 issues, 0 unresolved, $45.49)
- Implementation plan: `plans/2026-08-04-agent-memory-patterns.md` (5 tasks)
- Task 1 complete: ExperienceRecorder + ReflectionOrchestrator SPIs extracted in neocortex

### Open

- Tasks 2-5 of the implementation plan remain
- LLM-backed ReflectionSynthesizer (neocortex) deferred to follow-up
- Sub-epics B and C deferred to future branches
