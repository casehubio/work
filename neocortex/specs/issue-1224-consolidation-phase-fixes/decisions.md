## D1: Entity resolution via SurfaceFormTrie as cross-cutting infrastructure

**Choice:** Wire `SurfaceFormTrie` (from entity-token) into SubThoughtExtractor, MindMapExtractor, and MergeDetectionPhase as the primary entity recognition mechanism. Add `entity-token` as an optional dependency of `mindmap-intelligence` (pure Java, zero transitive deps). Use `Instance<SurfaceFormTrie>` for graceful degradation.
**Alternatives:**
- Build per-component entity matching — duplicates logic, inconsistent quality across paths
- Use MindMapExtractor's LLM-based extraction for everything — too slow for sub-thought classification, overkill for name matching
- Use SubThoughtClassifier.containsWord() — primitive, no alias handling, no canonical forms
**Rationale:** Entity recognition in text is a cross-cutting concern. SurfaceFormTrie already solves it (Aho-Corasick, aliases, word boundaries, canonical forms). Three separate findings (F6, F11, F19) are all building ad-hoc weak entity recognition where this strong one already exists. Reuse eliminates three classes of bug simultaneously.
**Trade-offs:** Adds an optional module dependency from mindmap-intelligence → entity-token. SurfaceFormTrie only recognizes entities already in the MindMap — truly novel entities won't be found until MindMapExtractor creates them and the trie rebuilds.
**Sources:** entity-token/SurfaceFormTrie.java, entity-token-cdi/SurfaceFormTrieManager.java, SubThoughtExtractor.java:49, MindMapExtractor.java:418, MergeDetectionPhase.java:145
**Exploration:** deep-analysis
**Status:** captured

## D2: Tenant-scoped consolidation state

**Choice:** Make tenant-awareness a first-class design constraint across all consolidation state: (1) key CognitiveAttentionAccumulator by (principalId, tenantId) composite, (2) add per-tenant swapAndReset to SignificanceAccumulator, (3) observe ExperienceRecorded for dynamic tenant registration.
**Alternatives:**
- Fix each finding independently — same underlying principle, applied inconsistently
- Global tenant state with post-hoc filtering — complex, error-prone, doesn't prevent state leakage
**Rationale:** F3, F5, F17 are the same design flaw manifested three ways. The consolidation scheduler treats tenant as an afterthought. Fixing them as one principle (consolidation state is tenant-scoped) is both simpler and more robust than three separate patches.
**Trade-offs:** Per-tenant state increases memory footprint proportionally to tenant count. Acceptable — tenant count is bounded and each tenant's state is small (a few signal lists and counters).
**Sources:** CognitiveAttentionAccumulator.java:34-35,96-110, ConsolidationScheduler.java:343-350, SignificanceAccumulator.java:97-105
**Exploration:** deep-analysis
**Status:** captured

## D3: Watermark cursor for experience graduation

**Choice:** Track the lowest unresolved (corroboration-blocked) memory ID. Set the persistent cursor to `min(lastProcessedId, lowestUnresolvedId - 1)` so blocked memories are always re-evaluated in future ticks.
**Alternatives:**
- Separate blocked list — additional state to persist and manage, risk of list growing unboundedly
- Two-pass cursor (process all, then re-scan blocked) — doubles scan cost per tick
- Bitmap of processed IDs — space-inefficient for large memory stores
**Rationale:** This is a classic watermark problem (TCP acknowledgement semantics). The cursor means "everything up to here is fully resolved." When a blocked memory sits at position N and a later one at N+5 graduates, the cursor stays at N-1. Simple, correct, no additional state structures.
**Trade-offs:** Re-scans memories between the watermark and the actual frontier on each tick. Acceptable — the scan window is bounded by the number of blocked memories, which is small relative to total memory count.
**Sources:** ExperienceConsolidationPhase.java:173-179,223
**Exploration:** quick
**Status:** captured

## D4: Type-based node naming for graduated experiences

**Choice:** Use classification info from DefaultGraduationClassifier to generate descriptive node names. Templates: "Belief: {subject} — {truncated text}" for observations, "Intention: {capability} — {truncated text}" for actions, "Judgment: {result} — {truncated text}" for outcomes. Fall back to current truncation when classification metadata is absent.
**Alternatives:**
- LLM summarization — too expensive for every graduation, adds latency to consolidation tick
- Raw text truncation (current) — produces verbose, non-descriptive names that are useless for graph navigation
**Rationale:** The classifier already distinguishes observation/action/outcome and extracts subject/capability/result. The naming template reuses this existing metadata to produce meaningful names at zero additional cost.
**Trade-offs:** Template-based names are formulaic. Acceptable — they're node names for graph navigation, not prose for display.
**Sources:** ExperienceConsolidationPhase.java:203-205, DefaultGraduationClassifier.java
**Exploration:** quick
**Status:** captured

## D5: Default confidence INFERRED for ConversationBridge

**Choice:** Change ConversationBridge's default ConfidenceOrigin from STATED (1.0) to INFERRED (0.7) when callers pass null. Callers with genuinely stated facts (biographical import) already pass STATED explicitly.
**Alternatives:**
- Make it configurable per invocation only — already parameterized, the issue is the default
- Use a lower value like SPECULATED (0.5) — too aggressive; conversation text has moderate reliability
**Rationale:** Conversation text is not stated fact — it's the system's interpretation of what was said. INFERRED (0.7) accurately reflects this. Biographical import callers already pass STATED explicitly, so changing the default only affects conversation processing.
**Trade-offs:** Existing nodes created before this change retain STATED 1.0. No migration needed — confidence decay normalizes over time.
**Sources:** ConversationBridge.java:62-63, MindMapConfidenceDefaults
**Exploration:** quick
**Status:** captured

## D6: Disable CommunitySummaryPhase by default

**Choice:** Gate CommunitySummaryPhase behind `@IfBuildProperty(name = "casehub.mindmap.consolidation.community-summary.enabled", stringValue = "true", enableIfMissing = false)`. No consumer exists for Summary-trait nodes. When enabled in future, the phase should use sessionPipeline (already wired) for real LLM summaries instead of placeholder text.
**Alternatives:**
- Remove the phase entirely — loses the k-core detection infrastructure which has value for future community analysis
- Generate real LLM summaries now — no consumer exists, so this produces better noise instead of placeholder noise
**Rationale:** Generating placeholder text that nothing reads is worse than generating nothing. The phase infrastructure (k-core detection, hash-based invalidation, stale cleanup) is worth preserving for when a consumer is built. Disabling via config preserves the code while eliminating runtime waste.
**Trade-offs:** Loses community detection as a side effect of consolidation. Acceptable — no downstream consumer means this side effect has no value today.
**Sources:** CommunitySummaryPhase.java:169-177, MergeDetectionPhase.java (excludes Summary-trait)
**Exploration:** quick
**Status:** captured

## D7: Safer merge thresholds with structural guards

**Choice:** Three changes: (1) MergeDetectionPhase: require minimum 2 shared neighbors for auto-merge — single-neighbor Jaccard is unreliable. (2) GoalResolutionPhase: add word-token Jaccard as secondary check alongside JW, and treat null contentDomain as incompatible (strict, not permissive). (3) Both phases: when entity-token is on classpath, check SurfaceFormTrie for distinct EntityIdentity entries — explicitly registered separate entities block merge regardless of name similarity.
**Alternatives:**
- Raise thresholds only — addresses symptoms, doesn't prevent category errors (similar names that are genuinely different)
- Make merge reversible — substantial complexity (undo log, edge restoration, property reconciliation) for a problem better solved by not merging incorrectly
- Require human confirmation for all merges — breaks autonomous consolidation
**Rationale:** The merge threshold formula isn't fundamentally wrong — the problem is insufficient evidence. JW captures character-level similarity but misses semantic distinctness. Word-token Jaccard catches "chapter 1" vs "chapter 2". Minimum neighbor count prevents single-neighbor inflation. EntityIdentity guard uses the system's own knowledge of distinct entities. Each guard addresses a different failure mode.
**Trade-offs:** Stricter merging means some genuine duplicates persist longer. Acceptable — a missed merge is recoverable (future tick catches it); a false merge is irreversible.
**Sources:** MergeDetectionPhase.java:53-66,145-153, GoalResolutionPhase.java:183-238
**Exploration:** deep-analysis
**Status:** captured

## D8: SubThoughtConsolidationPhase signals drain fix

**Choice:** Add `pendingSignals.clear()` after the copy in `signals()`, matching the drain pattern used by ExperienceConsolidationPhase, MergeDetectionPhase, and GoalResolutionPhase.
**Alternatives:**
- Rely on beginTick() clearing — creates a window where signals from one tick can appear in both that tick's collection and the next tick's, depending on call ordering
**Rationale:** Every other phase drains signals in `signals()`. This is the established pattern. The existing `beginTick()` clearing is a safety net, not the primary drain mechanism.
**Trade-offs:** None — this is a mechanical fix aligning with the existing pattern.
**Sources:** SubThoughtConsolidationPhase.java:87, ExperienceConsolidationPhase.java:137-140
**Exploration:** quick
**Status:** captured

## D9: SubThoughtExtractor entity resolution with parent SUBJECT fallback

**Choice:** After classifying type/text via SubThoughtClassifier, scan each sub-thought's text with SurfaceFormTrie (D1). If no entity found, fall back to the parent experience's SUBJECT attribute. If both are null, store the sub-thought without entity — it won't participate in entity-based consolidation grouping but is preserved for future ticks when the entity may appear in the graph.
**Alternatives:**
- Require entity for storage — loses type/text classification data for genuinely novel entities
- Text-similarity grouping for entityless sub-thoughts — overengineering; the SurfaceFormTrie + SUBJECT fallback covers the vast majority of cases
**Rationale:** Two-tier resolution (trie first, subject fallback) maximizes entity coverage without adding complexity. Novel entities are picked up in future ticks after MindMapExtractor creates them and the trie rebuilds.
**Trade-offs:** Entityless sub-thoughts accumulate until their entity appears in the graph. Bounded by memory retention policies.
**Depends on:** D1 (SurfaceFormTrie integration)
**Sources:** SubThoughtExtractor.java:49, SubThoughtConsolidationPhase.java:124, ExperienceAttributeKeys.SUBJECT
**Exploration:** quick
**Status:** captured

## D10: Fuzzy resolution scoped to subgraph with trie-first lookup

**Choice:** In MindMapExtractor, check SurfaceFormTrie for exact entity matches before falling back to FuzzyNodeResolver. When fuzzy resolution is needed, scope it to the relevant subgraph (pass subgraphId instead of null) to prevent cross-subgraph false positives.
**Alternatives:**
- Raise fuzzy threshold globally — reduces recall for legitimate fuzzy matches
- Remove fuzzy resolution — breaks handling of minor name variations and typos
**Rationale:** Exact trie lookup handles the common case (known entities) with zero false positives. Scoping fuzzy resolution to the current subgraph prevents "Mark" in PERSON matching "Marcus" in a different context. The threshold (0.85) is reasonable when scoped correctly.
**Trade-offs:** Cross-subgraph entity references require explicit linking rather than fuzzy inference. Acceptable — cross-subgraph references should be intentional, not accidental.
**Depends on:** D1 (SurfaceFormTrie integration)
**Sources:** MindMapExtractor.java:418, FuzzyNodeResolver.java
**Exploration:** quick
**Status:** captured
