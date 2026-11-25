# SE4: Consolidation Phase Fixes — Design

**Issue:** casehubio/neocortex#1224
**Parent:** casehubio/neocortex#1220 (Cognitive Pipeline Coherence)
**Module:** mindmap-intelligence (consolidation/ package)
**Date:** 2026-11-25

## Overview

Eight findings from the cognitive review affecting consolidation phases in
mindmap-intelligence. The fixes cluster around three cross-cutting principles
rather than eight independent patches.

## Cross-Cutting Principles

### 1. Entity resolution via SurfaceFormTrie

**Findings:** F6 (sub-thought entity null), F11 (merge false positives),
F19 (fuzzy matching too loose)

Three separate code paths build ad-hoc entity recognition where the codebase
already has a strong one. `SurfaceFormTrie` (entity-token module) provides
Aho-Corasick multi-pattern scanning with alias handling, word-boundary
filtering, and canonical forms. It's pure Java, zero deps, already maintained
by `SurfaceFormTrieManager` from MindMap entity data.

**Change:** Add `entity-token` as an optional dependency of
`mindmap-intelligence`. Use `Instance<SurfaceFormTrie>` for graceful
degradation. Wire it into:

- **SubThoughtExtractor** — scan sub-thought text for entity mentions after
  type classification. Fall back to parent experience SUBJECT attribute. (D1, D9)
- **MindMapExtractor** — trie-first lookup before FuzzyNodeResolver. Scope
  fuzzy resolution to the relevant subgraph instead of null/all. (D10)
- **MergeDetectionPhase** — when entities have distinct EntityIdentity
  entries, block merge regardless of name similarity. (D7)

**Limitation:** SurfaceFormTrie only recognizes entities already in the
MindMap. Novel entities are picked up in future ticks after MindMapExtractor
creates them and the trie rebuilds.

### 2. Tenant-scoped consolidation state

**Findings:** F3 (cross-tenant attention leakage), F5 (global significance
reset), F17 (no dynamic tenant discovery)

The consolidation scheduler treats tenant as an afterthought. Three findings
are the same design flaw: state that should be tenant-scoped is global.

**Changes:**

- **CognitiveAttentionAccumulator** (F3): Key by `(principalId, tenantId)`
  composite instead of principalId alone. The `PrincipalAttention` inner class
  becomes `AgentTenantAttention`. Briefings carry the correct tenantId. (D2)

- **SignificanceAccumulator** (F5): Add `swapAndReset(Set<String> tenantIds)`
  that only drains entries for the specified tenants. The scheduler calls this
  with the set of tenants being processed in the current tick, leaving others'
  accumulated significance intact. (D2)

- **ConsolidationScheduler** (F17): Add a CDI observer on `ExperienceRecorded`
  that calls `registerTenant(event.tenantId())` when a new tenantId appears.
  Tenants created after startup become visible to consolidation automatically. (D2)

### 3. Threshold safety for irreversible operations

**Findings:** F11 (merge too aggressive), F19 (confidence too high)

Irreversible operations need higher evidence bars than reversible ones. A
missed merge is recoverable (future tick catches it); a false merge is
permanent.

**Changes:**

- **MergeDetectionPhase** (F11): Require minimum 2 shared neighbors for
  auto-merge. Single-neighbor Jaccard is unreliable — one shared neighbor
  between two nodes yields Jaccard 1.0 if each has only one neighbor, inflating
  the blended score. Add SurfaceFormTrie guard per principle 1. (D7)

- **GoalResolutionPhase** (F11): Add word-token Jaccard as secondary check.
  Pure JW at 0.90 cannot distinguish "Write chapter 1" from "Write chapter 2"
  but word Jaccard can (they differ on "1" vs "2"). Treat null `contentDomain`
  as incompatible — null should mean "unknown domain," not "compatible with
  everything." (D7)

- **ConversationBridge** (F19): Change default ConfidenceOrigin from STATED
  (1.0) to INFERRED (0.7). Conversation text is the system's interpretation,
  not stated fact. Callers with genuinely stated facts (biographical import)
  already pass STATED explicitly. (D5)

## Individual Fixes

### F3: SubThoughtConsolidationPhase signals drain

Add `pendingSignals.clear()` after the copy in `signals()`, matching the
drain pattern used by ExperienceConsolidationPhase, MergeDetectionPhase, and
GoalResolutionPhase. (D8)

### F7: Experience graduation cursor skip

**Watermark cursor:** Track the lowest unresolved (corroboration-blocked)
memory ID during the scan. Set the persistent cursor to
`min(lastProcessedId, lowestUnresolvedId - 1)`. Blocked memories are always
re-evaluated in future ticks. (D3)

The re-scan window is bounded by the number of blocked memories, which is
small relative to total memory count.

### F7: Action/outcome subjects

Actions and outcomes not requiring corroboration is correct — they are
first-person experiences, not world-state observations that need
independent confirmation. However, they should populate SUBJECT when
available for grouping in SubThoughtConsolidationPhase. The
`DefaultGraduationClassifier` should extract SUBJECT from action/outcome
attributes where present (capability target for actions, result entity
for outcomes).

### F7: Graduated node names

Use classification info from `DefaultGraduationClassifier` to generate
descriptive node names via type-based templates: (D4)

| Cognitive kind | Template |
|---------------|----------|
| belief | `Belief: {subject} — {text≤60}` |
| intention | `Intention: {capability} — {text≤60}` |
| judgment | `Judgment: {result} — {text≤60}` |
| formative-experience | `Formative: {text≤80}` |

Fall back to current truncation when classification metadata is absent.

### F16: Community summary phase disabled

Gate behind `@IfBuildProperty(name = "casehub.mindmap.consolidation.community-summary.enabled", stringValue = "true", enableIfMissing = false)`. (D6)

The phase creates Summary-trait nodes that no consumer reads. The
infrastructure (k-core detection, hash invalidation, stale cleanup) is
preserved for future use. When enabled, the phase should use
`sessionPipeline` (already wired but unused) for real LLM summaries.

## Dependency Changes

```
mindmap-intelligence/pom.xml:
  + <dependency>
  +   <groupId>io.casehub</groupId>
  +   <artifactId>casehub-neocortex-entity-token</artifactId>
  +   <optional>true</optional>
  + </dependency>
```

No new transitive dependencies. `entity-token` is pure Java, zero deps.

## Test Strategy

Each fix requires both unit tests and verification that existing tests
(134 across affected files) continue to pass.

**New tests per finding:**

| Finding | New tests | What they verify |
|---------|-----------|-----------------|
| F6 | 3 | Trie entity extraction, SUBJECT fallback, null-entity storage |
| F3 (drain) | 1 | signals() drains after copy |
| F3 (tenant key) | 2 | Multi-tenant signal isolation, correct tenantId on briefing |
| F5 | 2 | Per-tenant swapAndReset, non-processed tenants retain significance |
| F7 (cursor) | 2 | Watermark stays behind blocked memory, re-evaluation on next tick |
| F7 (names) | 3 | Template per cognitive kind, fallback, metadata-absent case |
| F11 (merge) | 3 | Min-neighbor guard, word-Jaccard for goals, null-domain block |
| F16 | 1 | Phase disabled when property absent |
| F17 | 1 | Dynamic tenant registration via ExperienceRecorded |
| F19 (confidence) | 1 | Default INFERRED when origin null |
| F19 (fuzzy) | 2 | Trie-first lookup, subgraph-scoped fuzzy |

**~21 new tests total.**

Existing test suites to verify green: SubThoughtExtractorTest (8),
SubThoughtConsolidationPhaseTest (9), ExperienceConsolidationPhaseTest (18),
CognitiveAttentionAccumulatorTest (13), ConsolidationSchedulerTest (20),
MergeDetectionPhaseTest (8), GoalResolutionPhaseTest (17),
CommunitySummaryPhaseTest (4), ConversationBridgeTest (12), plus integration
tests (19).

## Ordering

The three principles can be implemented in any order — they touch
different files and have no mutual dependencies. Within principle 1
(entity-token integration), SubThoughtExtractor (F6) should be done
first since SubThoughtConsolidationPhase depends on it producing
non-null entities.

Suggested sequence:
1. F3 drain + F8 (mechanical, unblocks clean signal behavior)
2. F5 + F17 (tenant isolation — ConsolidationScheduler + SignificanceAccumulator)
3. F3 tenant key (CognitiveAttentionAccumulator)
4. F7 cursor + names (ExperienceConsolidationPhase)
5. F6 entity extraction (SubThoughtExtractor + entity-token dep)
6. F11 merge thresholds (MergeDetectionPhase + GoalResolutionPhase)
7. F19 confidence + fuzzy (ConversationBridge + MindMapExtractor)
8. F16 disable (CommunitySummaryPhase)

## References

- casehubio/neocortex#1224 — SE4 issue
- casehubio/neocortex#1220 — parent epic
- `docs/specs/cognitive-review-analysis/2026-11-24-cognitive-review-triage.md` — finding details
- `entity-token/src/main/java/io/casehub/neocortex/entity/token/SurfaceFormTrie.java` — Aho-Corasick scanner
- `entity-token-cdi/src/main/java/io/casehub/neocortex/entity/token/cdi/SurfaceFormTrieManager.java` — trie lifecycle
- `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/SubThoughtExtractor.java:49` — entity null
- `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/SubThoughtConsolidationPhase.java:87,124` — drain + skip
- `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/CognitiveAttentionAccumulator.java:34,96` — principal-only key
- `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/ConsolidationScheduler.java:343` — global swap
- `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/ExperienceConsolidationPhase.java:173,203,223` — cursor + names
- `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/MergeDetectionPhase.java:53,145` — thresholds
- `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/GoalResolutionPhase.java:183` — goal merge
- `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/CommunitySummaryPhase.java:169` — placeholder text
- `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/ConversationBridge.java:62` — STATED default
- `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/MindMapExtractor.java:418` — fuzzy 0.85
