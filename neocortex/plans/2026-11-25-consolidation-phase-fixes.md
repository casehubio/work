# SE4: Consolidation Phase Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #1224 — SE4: Consolidation Phase Fixes — graduation, entity resolution, scope
**Issue group:** #1224 (child of #1220)

**Goal:** Fix 8 findings (F3, F5, F6, F7, F11, F16, F17, F19) in the
mindmap-intelligence consolidation pipeline — entity resolution, tenant
isolation, merge safety, cursor correctness, and housekeeping.

**Architecture:** Three cross-cutting principles applied across 8 findings:
(1) SurfaceFormTrie for entity recognition reuse, (2) tenant-scoped
consolidation state, (3) safer thresholds for irreversible operations.
All changes in `mindmap-intelligence` module.

**Tech Stack:** Java 21, Quarkus CDI, SurfaceFormTrie (entity-token),
MindMapStore, CaseMemoryStore, JaroWinkler

## Global Constraints

- Java 21 source, Java 26 JVM
- Build: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install`
- Module test: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean test -pl mindmap-intelligence`
- All changes in `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/`
- Tests in `mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/`
- Every commit references `Refs #1224`
- Existing tests (134) must stay green

---

## Batch 1: Signal drain + tenant isolation (F3, F5, F17)

### Task 1: SubThoughtConsolidationPhase signals drain (F3 partial, D8)

**Files:**
- Modify: `consolidation/SubThoughtConsolidationPhase.java:87`
- Test: `consolidation/SubThoughtConsolidationPhaseTest.java`

**Interfaces:**
- Consumes: `ConsolidationPhase.signals()` contract (drain-on-read)
- Produces: No new interfaces — aligns with existing pattern

- [ ] **Step 1: Write failing test**

```java
@Test
void signals_drainsAfterRead() {
    // Setup: run a tick that produces signals (reuse concern escalation setup)
    phase.beginTick();

    // Add memories with concern sub-thoughts that trigger URGENCY_SPIKE
    var memory = createMemoryWithSubThought("concern", "worried about deadline", "project-1");
    storeMemory(memory, TENANT);
    phase.run(TENANT, List.of());

    // First read should return signals
    var first = phase.signals();
    assertFalse(first.isEmpty(), "Should have signals after run");

    // Second read should be empty (drained)
    var second = phase.signals();
    assertTrue(second.isEmpty(), "signals() should drain on read");
}
```

- [ ] **Step 2: Run test — expect FAIL** (second `signals()` returns same list)

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest="SubThoughtConsolidationPhaseTest#signals_drainsAfterRead" -Dsurefire.failIfNoSpecifiedTests=false`

- [ ] **Step 3: Fix signals() to drain**

In `SubThoughtConsolidationPhase.java`, change line 87:

```java
// Before:
@Override
public List<AttentionSignal> signals() { return List.copyOf(pendingSignals); }

// After:
@Override
public List<AttentionSignal> signals() {
    var result = List.copyOf(pendingSignals);
    pendingSignals.clear();
    return result;
}
```

- [ ] **Step 4: Run test — expect PASS**

- [ ] **Step 5: Run full SubThoughtConsolidationPhaseTest — expect all green**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest="SubThoughtConsolidationPhaseTest"`

- [ ] **Step 6: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/SubThoughtConsolidationPhase.java
git add mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/SubThoughtConsolidationPhaseTest.java
git commit -m "fix(mindmap-intelligence): drain signals in SubThoughtConsolidationPhase.signals()

Aligns with the drain-on-read pattern used by ExperienceConsolidationPhase,
MergeDetectionPhase, and GoalResolutionPhase. Without this, signals
accumulate unboundedly across ticks.

Refs #1224"
```

---

### Task 2: Per-tenant SignificanceAccumulator.swapAndReset (F5, D2)

**Files:**
- Modify: `consolidation/SignificanceAccumulator.java:97-105`
- Modify: `consolidation/ConsolidationScheduler.java:343-346`
- Test: `consolidation/SignificanceAccumulatorTest.java`
- Test: `consolidation/ConsolidationSchedulerTest.java`

**Interfaces:**
- Consumes: `SignificanceAccumulator.swapAndReset()` (existing)
- Produces: `SignificanceAccumulator.swapAndReset(Set<String> tenantIds)` (new overload)

- [ ] **Step 1: Write failing test for per-tenant swap**

In `SignificanceAccumulatorTest.java`:

```java
@Test
void swapAndReset_withTenantSet_onlyDrainsSpecifiedTenants() {
    // Accumulate significance for two tenants
    accumulator.onExperienceRecorded(eventForTenant("tenant-a", 3.0));
    accumulator.onExperienceRecorded(eventForTenant("tenant-b", 5.0));

    // Swap only tenant-a
    var snapshot = accumulator.swapAndReset(Set.of("tenant-a"));

    // tenant-a should be in the snapshot and drained
    assertEquals(3.0, snapshot.tenantSignificance().getOrDefault("tenant-a", 0.0), 0.01);

    // tenant-b should NOT be in the snapshot — still accumulating
    assertFalse(snapshot.tenantSignificance().containsKey("tenant-b"));

    // Full swap should now return tenant-b
    var fullSnapshot = accumulator.swapAndReset();
    assertEquals(5.0, fullSnapshot.tenantSignificance().getOrDefault("tenant-b", 0.0), 0.01);
}
```

- [ ] **Step 2: Run test — expect FAIL** (method doesn't exist)

- [ ] **Step 3: Add per-tenant swapAndReset to SignificanceAccumulator**

```java
public SignificanceSnapshot swapAndReset(Set<String> tenantIds) {
    var snapshot = new HashMap<String, Double>();
    for (String tenantId : tenantIds) {
        DoubleAdder adder = perTenant.remove(tenantId);
        if (adder != null) {
            snapshot.put(tenantId, adder.sum());
        }
        triggered.remove(tenantId);
    }
    return new SignificanceSnapshot(Map.copyOf(snapshot));
}
```

- [ ] **Step 4: Run test — expect PASS**

- [ ] **Step 5: Write test that ConsolidationScheduler uses per-tenant swap**

In `ConsolidationSchedulerTest.java`:

```java
@Test
void tick_onlySwapsSignificanceForRunTenants() {
    // Register two tenants
    scheduler.registerTenant("tenant-a");
    scheduler.registerTenant("tenant-b");

    // Accumulate significance for both
    significanceAccumulator.onExperienceRecorded(eventForTenant("tenant-a", 3.0));
    significanceAccumulator.onExperienceRecorded(eventForTenant("tenant-b", 5.0));

    // Simulate busy state so only starved tenants run
    // (configure so only tenant-a is starved)
    // ... run tick ...

    // tenant-b's significance should be preserved
    var remaining = significanceAccumulator.swapAndReset();
    assertTrue(remaining.tenantSignificance().containsKey("tenant-b"));
}
```

- [ ] **Step 6: Update ConsolidationScheduler to use per-tenant swap**

In `ConsolidationScheduler.java`, modify `beginTickAllPhases` to accept the tenant set and use per-tenant swap:

```java
private void beginTickAllPhases(Set<String> tenantsToRun) {
    if (significanceAccumulator != null) {
        significanceAccumulator.swapAndReset(tenantsToRun);
    }
    for (ConsolidationPhase phase : phases) {
        phase.beginTick();
    }
}
```

Update callers in `tick()` (line 177) and `runConsolidation()` (line 223):
- `tick()`: `beginTickAllPhases(Set.copyOf(tenantsToRun));`
- `runConsolidation()`: `beginTickAllPhases(Set.of(tenantId));`

- [ ] **Step 7: Run all ConsolidationSchedulerTest + SignificanceAccumulatorTest — expect green**

- [ ] **Step 8: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/SignificanceAccumulator.java
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/ConsolidationScheduler.java
git add mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/SignificanceAccumulatorTest.java
git add mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/ConsolidationSchedulerTest.java
git commit -m "fix(mindmap-intelligence): per-tenant SignificanceAccumulator.swapAndReset

Previously swapAndReset() replaced the entire perTenant map atomically,
discarding accumulated significance for all tenants even when only a
subset was being processed. New overload swapAndReset(Set<String>) only
drains entries for the specified tenants.

Refs #1224"
```

---

### Task 3: Dynamic tenant discovery + tenant-scoped attention (F17, F3 partial, D2)

**Files:**
- Modify: `consolidation/ConsolidationScheduler.java` (add CDI observer)
- Modify: `consolidation/CognitiveAttentionAccumulator.java:34,48,96-100`
- Test: `consolidation/ConsolidationSchedulerTest.java`
- Test: `consolidation/CognitiveAttentionAccumulatorTest.java`

**Interfaces:**
- Consumes: `ExperienceRecorded` CDI event
- Produces: Composite key `(principalId, tenantId)` in CognitiveAttentionAccumulator

- [ ] **Step 1: Write failing test for dynamic tenant registration**

In `ConsolidationSchedulerTest.java`:

```java
@Test
void onExperienceRecorded_registersNewTenant() {
    // No tenants initially
    assertTrue(scheduler.knownTenants().isEmpty());

    // Fire an ExperienceRecorded event for a new tenant
    scheduler.onExperienceRecorded(experienceRecordedForTenant("new-tenant"));

    // Tenant should now be known
    assertTrue(scheduler.knownTenants().contains("new-tenant"));
}
```

- [ ] **Step 2: Run test — expect FAIL**

- [ ] **Step 3: Add CDI observer to ConsolidationScheduler**

```java
void onExperienceRecorded(@Observes ExperienceRecorded event) {
    String tenantId = event.event().tenantId();
    if (tenantId != null) {
        knownTenants.add(tenantId);
    }
}
```

Add a package-private accessor for testing: `Set<String> knownTenants() { return Set.copyOf(knownTenants); }`

- [ ] **Step 4: Run test — expect PASS**

- [ ] **Step 5: Write failing test for tenant-scoped attention key**

In `CognitiveAttentionAccumulatorTest.java`:

```java
@Test
void signals_isolatedByTenant_samePrincipal() {
    // Same principal, different tenants
    var signalA = new AttentionSignal("agent-1", "tenant-a",
        SignalCategory.URGENCY_SPIKE, "node-1", "test", 10.0, "urgent");
    var signalB = new AttentionSignal("agent-1", "tenant-b",
        SignalCategory.URGENCY_SPIKE, "node-2", "test", 10.0, "urgent");

    accumulator.addSignals(List.of(signalA));

    // Should NOT fire — tenant-a alone is below threshold
    // (adjust significance so only combined would cross threshold)
    // Then add tenant-b signals

    accumulator.addSignals(List.of(signalB));

    // Verify briefings carry correct tenantId
    // The two tenants should fire independently, each with their own tenantId
    assertEquals(2, firedEvents.size());
    var tenantIds = firedEvents.stream()
        .map(e -> e.briefing().tenantId())
        .collect(Collectors.toSet());
    assertEquals(Set.of("tenant-a", "tenant-b"), tenantIds);
}
```

- [ ] **Step 6: Run test — expect FAIL** (signals pooled under same principal)

- [ ] **Step 7: Implement tenant-scoped attention key**

Change `CognitiveAttentionAccumulator`:

1. Replace `ConcurrentHashMap<String, PrincipalAttention>` with composite key:

```java
private final ConcurrentHashMap<String, PrincipalAttention> perAgentTenant =
    new ConcurrentHashMap<>();

private static String compositeKey(String principalId, String tenantId) {
    return principalId + "::" + (tenantId != null ? tenantId : "__global__");
}
```

2. Update `addSignals()` to use composite key:

```java
String key = signal.principalId() != null
    ? compositeKey(signal.principalId(), signal.tenantId())
    : "__broadcast__";
```

3. Update `evaluateThreshold()` to extract tenantId from the composite key for the briefing.

4. Update `updateUrgencyP75` to accept `(principalId, tenantId)`.

- [ ] **Step 8: Run test — expect PASS**

- [ ] **Step 9: Run all CognitiveAttentionAccumulatorTest + ConsolidationSchedulerTest — expect green**

- [ ] **Step 10: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/CognitiveAttentionAccumulator.java
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/ConsolidationScheduler.java
git add mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/CognitiveAttentionAccumulatorTest.java
git add mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/ConsolidationSchedulerTest.java
git commit -m "fix(mindmap-intelligence): tenant-scoped attention + dynamic tenant discovery

CognitiveAttentionAccumulator now keys by (principalId, tenantId) composite
instead of principalId alone, preventing cross-tenant signal leakage.
ConsolidationScheduler observes ExperienceRecorded events to register new
tenants dynamically instead of only discovering them at startup.

Refs #1224"
```

---

## Batch 2: Cursor + naming fixes (F7, D3, D4)

### Task 4: Watermark cursor with TTL escape hatch (F7 cursor, D3)

**Files:**
- Modify: `consolidation/ExperienceConsolidationPhase.java:161-224`
- Test: `consolidation/ExperienceConsolidationPhaseTest.java`

**Interfaces:**
- Consumes: `CaseMemoryStore.scan()`, `GraduationScorer`, `GraduationClassifier`
- Produces: No new interfaces — internal cursor logic change

- [ ] **Step 1: Write failing test — cursor stays behind blocked memory**

```java
@Test
void cursorStaysBehindCorroborationBlockedMemory() {
    // Create 3 memories: mem-1 (blocked), mem-2 (passes), mem-3 (passes)
    // mem-1 has subject "rare-entity" with 0 corroboration
    // mem-2, mem-3 have subject "common-entity" with 3 corroborations

    phase.run(TENANT, List.of());

    // Cursor should be BEFORE mem-1, not at mem-3
    String cursor = loadCursor(TENANT);
    // mem-1 should be re-evaluated on next run
    // Run again — mem-1 should appear in the scan
    phase.run(TENANT, List.of());
    // Verify mem-1 was re-evaluated (would have been skipped before this fix)
}
```

- [ ] **Step 2: Run test — expect FAIL** (cursor jumps to mem-3)

- [ ] **Step 3: Implement watermark cursor**

In `ExperienceConsolidationPhase.run()`, track the lowest blocked ID:

```java
String lowestBlockedId = null;
// ... inside the loop, when corroboration-blocked:
if (corroborationBlocked) {
    if (lowestBlockedId == null) {
        lowestBlockedId = memory.memoryId();
    }
}
```

After the loop, adjust the persistent cursor:

```java
// Watermark: cursor advances only to the contiguous processed point
if (lowestBlockedId != null && lowestBlockedId.compareTo(lastProcessedId) < 0) {
    // A blocked memory sits before the last processed — rewind cursor
    // to just before the blocked memory so it's re-scanned next tick
    lastProcessedId = previousIdOf(lowestBlockedId);
}
```

For the "previous ID" logic: since IDs are monotonic longs stored as strings, subtract 1. If blocked memory is the first in the batch, use the original cursor value.

Add TTL tracking: store a property `blocked-ticks:<memoryId>` on the cursor sentinel node. Increment each tick. When >= 10, abandon (advance cursor past it).

- [ ] **Step 4: Run test — expect PASS**

- [ ] **Step 5: Write TTL escape hatch test**

```java
@Test
void ttlEscapeHatch_abandonsAfterMaxTicks() {
    // Create a permanently-blocked memory (subject never gets corroboration)
    // Run phase 11 times
    // On tick 11, cursor should advance past the blocked memory
}
```

- [ ] **Step 6: Implement TTL tracking** (increment counter property, check >= threshold)

- [ ] **Step 7: Run test — expect PASS**

- [ ] **Step 8: Run all ExperienceConsolidationPhaseTest — expect green**

- [ ] **Step 9: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/ExperienceConsolidationPhase.java
git add mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/ExperienceConsolidationPhaseTest.java
git commit -m "fix(mindmap-intelligence): watermark cursor with TTL escape for experience graduation

Cursor now stays behind corroboration-blocked memories so they are
re-evaluated in future ticks. TTL escape hatch abandons permanently-blocked
memories after 10 ticks to prevent watermark livelock.

Refs #1224"
```

---

### Task 5: Type-based graduated node naming (F7 names, D4)

**Files:**
- Modify: `consolidation/ExperienceConsolidationPhase.java:203-206`
- Test: `consolidation/ExperienceConsolidationPhaseTest.java`

**Interfaces:**
- Consumes: `GraduationResult.cognitiveKind()`, `GraduationResult.properties()`
- Produces: Descriptive node names (e.g., "Belief: Alice — observed frustration...")

- [ ] **Step 1: Write failing tests for each naming template**

```java
@Test
void graduatedNodeName_belief_includesSubject() {
    // Observation with subject "Alice"
    var memory = createObservationMemory("Alice seemed frustrated about the deadline", "Alice");
    storeWithCorroboration(memory, TENANT, 3);

    phase.run(TENANT, List.of());

    var nodes = nodesInCognitiveSubgraph(TENANT);
    assertEquals(1, nodes.size());
    assertTrue(nodes.get(0).name().startsWith("Belief: Alice —"));
}

@Test
void graduatedNodeName_intention_includesCapability() {
    var memory = createActionMemory("Sent deployment notification", "deploy");
    storeWithCorroboration(memory, TENANT, 3);

    phase.run(TENANT, List.of());

    var nodes = nodesInCognitiveSubgraph(TENANT);
    assertTrue(nodes.get(0).name().startsWith("Intention: deploy —"));
}

@Test
void graduatedNodeName_formative_usesLongerTruncation() {
    var memory = createFormativeMemory("A very long formative experience text...");
    storeMemory(memory, TENANT);

    phase.run(TENANT, List.of());

    var nodes = nodesInCognitiveSubgraph(TENANT);
    assertTrue(nodes.get(0).name().startsWith("Formative:"));
    assertTrue(nodes.get(0).name().length() <= 92); // "Formative: " + 80 + "..."
}
```

- [ ] **Step 2: Run tests — expect FAIL** (names are raw text truncation)

- [ ] **Step 3: Extract node naming into a helper method**

In `ExperienceConsolidationPhase`, replace lines 203-206 with:

```java
String name = graduatedNodeName(result, memory);
```

Add the helper:

```java
static String graduatedNodeName(GraduationResult result, Memory memory) {
    String rawText = memory.text() != null ? memory.text() : "";
    return switch (result.cognitiveKind()) {
        case "belief" -> {
            String subject = result.properties().getOrDefault("subject", "");
            yield subject.isEmpty()
                ? truncate("Belief: " + rawText, 70)
                : truncate("Belief: " + subject + " — " + rawText, 70);
        }
        case "intention" -> {
            String capability = result.properties().getOrDefault("goal", "");
            yield capability.isEmpty()
                ? truncate("Intention: " + rawText, 70)
                : truncate("Intention: " + capability + " — " + rawText, 70);
        }
        case "judgment" -> {
            String target = result.properties().getOrDefault("target", "");
            yield target.isEmpty()
                ? truncate("Judgment: " + rawText, 70)
                : truncate("Judgment: " + target + " — " + rawText, 70);
        }
        case "formative-experience" ->
            truncate("Formative: " + rawText, 92);
        default ->
            truncate(rawText, 100);
    };
}

private static String truncate(String text, int maxLen) {
    return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
}
```

- [ ] **Step 4: Run tests — expect PASS**

- [ ] **Step 5: Run all ExperienceConsolidationPhaseTest — expect green**

- [ ] **Step 6: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/ExperienceConsolidationPhase.java
git add mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/ExperienceConsolidationPhaseTest.java
git commit -m "fix(mindmap-intelligence): type-based naming for graduated experience nodes

Graduated node names now use classification metadata:
Belief: {subject} — {text}, Intention: {capability} — {text},
Judgment: {result} — {text}, Formative: {text}. Falls back to raw text
truncation for unknown cognitive kinds.

Refs #1224"
```

---

## Batch 3: Entity extraction + sub-thought consolidation (F6, D1, D9)

### Task 6: SubThoughtExtractor entity resolution via SurfaceFormTrie (F6, D1, D9)

**Files:**
- Modify: `SubThoughtExtractor.java:26-30,47-51`
- Test: `SubThoughtExtractorTest.java`

**Interfaces:**
- Consumes: `Instance<SurfaceFormTrie>`, `CaseMemoryStore.retrieve()` (for parent SUBJECT)
- Produces: `ParsedSubThought` with non-null entity field

- [ ] **Step 1: Write failing test — trie resolves entity**

```java
@Test
void extractFromText_resolvesEntityViaTrie() {
    // Build a SurfaceFormTrie with known entity "Alice"
    var identity = EntityIdentity.of("person", "alice-1", "Alice",
        List.of(), List.of(), List.of("Alice"));
    var trie = SurfaceFormTrie.build(List.of(identity));

    var extractor = new SubThoughtExtractor(memoryStore, enrichedEvent, trie);

    var subThoughts = extractor.extractFromText("Alice seemed frustrated");
    assertFalse(subThoughts.isEmpty());
    assertEquals("Alice", subThoughts.get(0).entity());
}
```

- [ ] **Step 2: Run test — expect FAIL** (entity is null)

- [ ] **Step 3: Add SurfaceFormTrie to SubThoughtExtractor**

Add `Instance<SurfaceFormTrie>` as optional CDI dependency:

```java
private final SurfaceFormTrie trie;

@Inject
public SubThoughtExtractor(CaseMemoryStore memoryStore,
                            jakarta.enterprise.event.Event<SubThoughtsEnriched> enrichedEvent,
                            Instance<SurfaceFormTrie> trieInstance) {
    this.memoryStore = memoryStore;
    this.enrichedEvent = enrichedEvent;
    this.trie = trieInstance.isResolvable() ? trieInstance.get() : null;
}

// Test-friendly constructor
SubThoughtExtractor(CaseMemoryStore memoryStore,
                     jakarta.enterprise.event.Event<SubThoughtsEnriched> enrichedEvent,
                     SurfaceFormTrie trie) {
    this.memoryStore = memoryStore;
    this.enrichedEvent = enrichedEvent;
    this.trie = trie;
}
```

Update `extractFromText` to resolve entities:

```java
List<ParsedSubThought> extractFromText(String text) {
    return SubThoughtClassifier.classify(text).stream()
            .map(m -> new ParsedSubThought(m.type(), m.text(),
                    resolveEntity(m.text()), 0.8))
            .toList();
}

private String resolveEntity(String text) {
    if (trie == null || text == null) return null;
    var matches = trie.scan(text);
    return matches.isEmpty() ? null : matches.get(0).entityId();
}
```

- [ ] **Step 4: Run test — expect PASS**

- [ ] **Step 5: Write test for SUBJECT fallback**

```java
@Test
void onExtractionRequested_fallsBackToParentSubject() {
    // No trie available
    var extractor = new SubThoughtExtractor(memoryStore, enrichedEvent, (SurfaceFormTrie) null);

    // Store a memory with SUBJECT attribute
    var memoryId = storeMemoryWithSubject("unknown entity mentioned here", "parent-subject");

    // Fire extraction
    extractor.onExtractionRequested(new SubThoughtExtractionRequested(
        memoryId, TENANT, "unknown entity mentioned here", null));

    // Verify sub-thoughts have entity = "parent-subject" (from parent memory)
    var attrs = memoryStore.retrieve(memoryId, TENANT).attributes();
    assertEquals("parent-subject", attrs.get(SubThoughtAttributeKeys.entity(0)));
}
```

- [ ] **Step 6: Implement SUBJECT fallback in onExtractionRequested**

After `extractFromText`, read the parent memory's SUBJECT attribute and backfill any sub-thoughts with null entity:

```java
var parentMemory = memoryStore.retrieve(event.memoryId(), event.tenantId());
String parentSubject = parentMemory != null
    ? parentMemory.attributes().get(ExperienceAttributeKeys.SUBJECT)
    : null;

if (parentSubject != null) {
    subThoughts = subThoughts.stream()
        .map(st -> st.entity() == null
            ? new ParsedSubThought(st.type(), st.text(), parentSubject, st.confidence())
            : st)
        .toList();
}
```

- [ ] **Step 7: Run test — expect PASS**

- [ ] **Step 8: Write test for null entity storage (no trie, no subject)**

```java
@Test
void extractFromText_nullEntity_storedWithoutEntityAttribute() {
    var extractor = new SubThoughtExtractor(memoryStore, enrichedEvent, (SurfaceFormTrie) null);
    var subThoughts = extractor.extractFromText("some text with no known entities");
    // Sub-thoughts should still be classified by type
    assertFalse(subThoughts.isEmpty());
    // Entity should be null — stored without entity attribute
    assertNull(subThoughts.get(0).entity());
}
```

- [ ] **Step 9: Run test — expect PASS** (already works, confirms no regression)

- [ ] **Step 10: Run all SubThoughtExtractorTest — expect green**

- [ ] **Step 11: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/SubThoughtExtractor.java
git add mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/SubThoughtExtractorTest.java
git commit -m "fix(mindmap-intelligence): SubThoughtExtractor entity resolution via SurfaceFormTrie

Scan sub-thought text with SurfaceFormTrie (Aho-Corasick) for entity
recognition. Fall back to parent experience SUBJECT attribute. Without
entity resolution, SubThoughtConsolidationPhase was completely inert.

Refs #1224"
```

---

## Batch 4: Merge safety (F11, D7)

### Task 7: MergeDetectionPhase minimum-neighbor guard (F11, D7)

**Files:**
- Modify: `consolidation/MergeDetectionPhase.java:140-165`
- Test: `consolidation/MergeDetectionPhaseTest.java`

**Interfaces:**
- Consumes: `MindMapStore.neighbors()`, `JaroWinkler.similarity()`
- Produces: Stricter merge candidates

- [ ] **Step 1: Write failing test — single neighbor blocks auto-merge**

```java
@Test
void autoMerge_blockedBySingleSharedNeighbor() {
    // Create "John Smith" and "Joan Smith" sharing exactly 1 neighbor
    var john = addNode("John Smith", subgraphId, TENANT);
    var joan = addNode("Joan Smith", subgraphId, TENANT);
    var shared = addNode("Project X", subgraphId, TENANT);
    addEdge(john, shared, "works-on", TENANT);
    addEdge(joan, shared, "works-on", TENANT);

    phase.run(TENANT, List.of());

    // Both nodes should still exist (not merged)
    assertNotNull(store.getNode(john, TENANT));
    assertNotNull(store.getNode(joan, TENANT));
}
```

- [ ] **Step 2: Run test — expect FAIL** (nodes auto-merged because 0.6*0.88 + 0.4*1.0 = 0.928 >= 0.9)

- [ ] **Step 3: Add minimum neighbor guard**

In `detectCandidates()`, after computing `neighborOverlap`:

```java
int sharedCount = (int) neighborsA.stream()
    .filter(neighborsB::contains).count();
int totalNeighborCount = neighborsA.size() + neighborsB.size();

// Require minimum 2 shared neighbors for auto-merge,
// unless both nodes have zero neighbors and JW >= 0.95
boolean zeroNeighborExemption = totalNeighborCount == 0 && nameSim >= 0.95;
boolean sufficientEvidence = sharedCount >= 2 || zeroNeighborExemption;

double combined = nameWeight() * nameSim + neighborWeight() * neighborOverlap;
if (combined >= candidateMinScore() && sufficientEvidence) {
    // ... add candidate for auto-merge
} else if (combined >= candidateMinScore()) {
    // Flag for review only — insufficient evidence for auto-merge
    String reason = "name-similarity (insufficient neighbors for auto-merge)";
    candidates.add(new MergeCandidate(
        a.id(), b.id(), Math.min(combined, reviewThreshold() + 0.01), reason, Instant.now()));
}
```

Wait — the guard should affect auto-merge eligibility, not candidate generation. The candidates are scored and then auto-merge is decided in `run()` at line 96. The simplest fix: cap the score below `autoMergeThreshold()` when `sharedCount < 2` and not zero-neighbor-exempt:

```java
if (!sufficientEvidence) {
    combined = Math.min(combined, autoMergeThreshold() - 0.01);
}
```

- [ ] **Step 4: Run test — expect PASS**

- [ ] **Step 5: Write test for zero-neighbor exemption**

```java
@Test
void autoMerge_allowedForZeroNeighborHighSimilarity() {
    // Create two "Alice" nodes with no neighbors (JW = 1.0)
    var alice1 = addNode("Alice", subgraphId, TENANT);
    var alice2 = addNode("Alice", subgraphId, TENANT);

    phase.run(TENANT, List.of());

    // One should be merged (only one survives)
    // Check that one node was removed
}
```

- [ ] **Step 6: Run test — expect PASS**

- [ ] **Step 7: Run all MergeDetectionPhaseTest — expect green**

- [ ] **Step 8: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/MergeDetectionPhase.java
git add mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/MergeDetectionPhaseTest.java
git commit -m "fix(mindmap-intelligence): require min 2 shared neighbors for auto-merge

Single-neighbor Jaccard inflates the blended score unreliably. Zero-neighbor
nodes with JW >= 0.95 are exempt (newly extracted duplicates). Candidates
with insufficient evidence are flagged for review instead of auto-merged.

Refs #1224"
```

---

### Task 8: GoalResolutionPhase word-token Jaccard + null domain guard (F11, D7)

**Files:**
- Modify: `consolidation/GoalResolutionPhase.java:183-238`
- Test: `consolidation/GoalResolutionPhaseTest.java`

**Interfaces:**
- Consumes: `MindMapStore.mergeNodes()`, `JaroWinkler`
- Produces: Stricter goal merge criteria

- [ ] **Step 1: Write failing test — word-Jaccard blocks chapter merge**

```java
@Test
void merge_doesNotMergeGoalsWithDifferentWordTokens() {
    // "Write chapter 1" and "Write chapter 2" — JW >= 0.90 but word Jaccard < 1.0
    var goal1 = addGoalNode("Write chapter 1", TENANT);
    var goal2 = addGoalNode("Write chapter 2", TENANT);

    phase.run(TENANT, List.of());

    // Both goals should still exist
    assertNotNull(store.getNode(goal1, TENANT));
    assertNotNull(store.getNode(goal2, TENANT));
}
```

- [ ] **Step 2: Run test — expect FAIL** (JW("Write chapter 1", "Write chapter 2") ≈ 0.95)

- [ ] **Step 3: Add word-token Jaccard check**

In `GoalResolutionPhase.merge()`, after the JW check:

```java
double similarity = JaroWinkler.similarity(a.name(), b.name());
if (similarity < 0.90) continue;

// Word-token Jaccard guard: catch "Write chapter 1" vs "Write chapter 2"
Set<String> wordsA = Set.of(a.name().toLowerCase().split("\\s+"));
Set<String> wordsB = Set.of(b.name().toLowerCase().split("\\s+"));
double wordJaccard = jaccard(wordsA, wordsB);
if (wordJaccard < 0.8) continue;
```

Also fix null domain compatibility:

```java
// Before:
boolean domainCompatible = domainA == null || domainB == null || domainA.equals(domainB);
// After:
boolean domainCompatible = domainA != null && domainB != null && domainA.equals(domainB);
```

Wait — if BOTH are null, they might still be duplicates. Only block when one is null and the other isn't, or when both are non-null and different:

```java
boolean domainCompatible = Objects.equals(domainA, domainB);
```

This means two null-domain goals ARE compatible (both unknown = same as each other), and null vs non-null are incompatible. This is stricter than before (was treating null as wildcard) but still allows same-unknown-domain merges.

- [ ] **Step 4: Run test — expect PASS**

- [ ] **Step 5: Write test for null domain guard**

```java
@Test
void merge_blocksWhenOneDomainNullOtherSet() {
    var goal1 = addGoalNode("Learn Python", TENANT);
    setProperty(goal1, "contentDomain", "data-science", TENANT);
    var goal2 = addGoalNode("Learn Python", TENANT); // no domain

    phase.run(TENANT, List.of());

    // Both should survive — null != "data-science"
    assertNotNull(store.getNode(goal1, TENANT));
    assertNotNull(store.getNode(goal2, TENANT));
}
```

- [ ] **Step 6: Run test — expect PASS**

- [ ] **Step 7: Run all GoalResolutionPhaseTest — expect green**

- [ ] **Step 8: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/GoalResolutionPhase.java
git add mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/GoalResolutionPhaseTest.java
git commit -m "fix(mindmap-intelligence): word-token Jaccard + strict domain for goal merge

Pure JW at 0.90 collapsed 'Write chapter 1' / 'Write chapter 2'. Word-token
Jaccard < 0.8 now blocks merge. Null contentDomain is no longer treated as
universally compatible.

Refs #1224"
```

---

## Batch 5: Confidence, fuzzy resolution, community summary (F19, F16, D5, D6, D10)

### Task 9: ConversationBridge default INFERRED confidence (F19, D5)

**Files:**
- Modify: `ConversationBridge.java:62-63`
- Test: `ConversationBridgeTest.java`

**Interfaces:**
- Consumes: `ConfidenceOrigin.INFERRED`, `MindMapConfidenceDefaults.forOrigin()`
- Produces: Nodes with INFERRED (0.7) confidence by default

- [ ] **Step 1: Modify existing test expectation**

The existing test `process_nullConfidence_defaultsToStated` should be renamed and updated to expect INFERRED:

```java
@Test
void process_nullConfidence_defaultsToInferred() {
    // ... same setup, null confidence origin
    // Assert node confidence is 0.7 (INFERRED), not 1.0 (STATED)
}
```

- [ ] **Step 2: Run test — expect FAIL** (default is STATED)

- [ ] **Step 3: Change default from STATED to INFERRED**

```java
// Before:
ConfidenceOrigin origin = confidenceOrigin != null ? confidenceOrigin : ConfidenceOrigin.STATED;
// After:
ConfidenceOrigin origin = confidenceOrigin != null ? confidenceOrigin : ConfidenceOrigin.INFERRED;
```

- [ ] **Step 4: Run test — expect PASS**

- [ ] **Step 5: Run all ConversationBridgeTest — expect green**

Existing test `process_inferredConfidence_createsNodesAt07` already validates INFERRED behavior. The only test that changes expectation is the null-confidence test.

- [ ] **Step 6: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/ConversationBridge.java
git add mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/ConversationBridgeTest.java
git commit -m "fix(mindmap-intelligence): default ConversationBridge confidence to INFERRED

Conversation text is the system's interpretation, not stated fact. STATED
(1.0) overstated certainty. INFERRED (0.7) is accurate. Callers with
genuinely stated facts (biographical import) already pass STATED explicitly.

Refs #1224"
```

---

### Task 10: MindMapExtractor two-tier scoped fuzzy resolution (F19, D10)

**Files:**
- Modify: `MindMapExtractor.java:418` (and surrounding resolution logic)
- Modify: `FuzzyNodeResolver.java` (add subgraph-scoped variant if needed)
- Test: `MindMapExtractorTest.java` (or create if not present)

**Interfaces:**
- Consumes: `Instance<SurfaceFormTrie>`, `FuzzyNodeResolver.resolve()`
- Produces: Trie-first resolution, two-tier scoped fuzzy

- [ ] **Step 1: Write failing test — trie-first resolution**

```java
@Test
void resolve_usesTrieBeforeFuzzy() {
    // Create entity "Alice" in MindMap
    var aliceId = store.addNode(NodeInput.of("Alice", subgraphId), TENANT);
    // Build trie with Alice
    var trie = buildTrieFromStore(TENANT);

    // Extract text mentioning "Alice"
    var extractor = new MindMapExtractor(store, sessionPipeline, trie);
    extractor.extract("Alice visited the museum", TENANT, null);

    // Should resolve to existing Alice node (trie match), not create a new one
    assertEquals(1, store.search(MindMapQuery.text("Alice", TENANT)).size());
}
```

- [ ] **Step 2: Write test — scoped fuzzy, then global at higher threshold**

```java
@Test
void resolve_scopedFuzzyFirst_thenGlobalAtHigherThreshold() {
    // Create "Dr. Smith" in PERSON subgraph
    var personSg = ensureSubgraph("person", TENANT);
    var smithId = store.addNode(NodeInput.of("Dr. Smith", personSg), TENANT);

    // Process text in GENERAL subgraph mentioning "Dr. Smth" (typo)
    var generalSg = ensureSubgraph("general", TENANT);

    // Scoped fuzzy within GENERAL finds nothing
    // Global fuzzy at 0.95 threshold: JW("Dr. Smith", "Dr. Smth") ≈ 0.93 < 0.95
    // So typo is NOT matched cross-subgraph — creates new node
    // This is correct behavior: 0.93 is below the global threshold
}
```

- [ ] **Step 3: Implement trie-first + two-tier fuzzy**

In `MindMapExtractor`, before fuzzy resolution:

```java
// Tier 0: Exact match via SurfaceFormTrie
if (trie != null) {
    var trieMatches = trie.scan(name);
    if (!trieMatches.isEmpty()) {
        return Optional.of(store.getNode(trieMatches.get(0).entityId(), tenantId));
    }
}

// Tier 1: Fuzzy within current subgraph
Optional<MindMapNode> scopedMatch = fuzzyResolver.resolve(
    store, name, 0.85, subgraphId, tenantId);
if (scopedMatch.isPresent()) return scopedMatch;

// Tier 2: Global fuzzy at higher threshold (cross-subgraph typo recovery)
return fuzzyResolver.resolve(store, name, 0.95, null, tenantId);
```

- [ ] **Step 4: Run tests — expect PASS**

- [ ] **Step 5: Run full module tests — expect green**

- [ ] **Step 6: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/MindMapExtractor.java
git add mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/MindMapExtractorTest.java
git commit -m "fix(mindmap-intelligence): trie-first + two-tier scoped fuzzy resolution

SurfaceFormTrie exact match first, then scoped fuzzy within subgraph at 0.85,
then global fuzzy at 0.95 for cross-subgraph typo recovery. Prevents
cross-subgraph false positives while handling genuine typos.

Refs #1224"
```

---

### Task 11: Disable CommunitySummaryPhase by default (F16, D6)

**Files:**
- Modify: `consolidation/CommunitySummaryPhase.java:28-50`
- Test: `consolidation/CommunitySummaryPhaseTest.java`

**Interfaces:**
- Consumes: `@ConfigProperty` for enabled flag
- Produces: No-op when disabled

- [ ] **Step 1: Write failing test — phase disabled when property absent**

```java
@Test
void run_disabledByDefault_doesNothing() {
    // Create a triangle of connected nodes (would trigger community detection)
    var a = addNode("A", subgraphId, TENANT);
    var b = addNode("B", subgraphId, TENANT);
    var c = addNode("C", subgraphId, TENANT);
    addEdge(a, b, "knows", TENANT);
    addEdge(b, c, "knows", TENANT);
    addEdge(a, c, "knows", TENANT);

    // Phase with enabled=false
    var phase = new CommunitySummaryPhase(store, 3, 3, 5, false);
    phase.run(TENANT, List.of());

    // No Summary nodes created
    var summaries = store.search(MindMapQuery.text("Summary:", TENANT));
    assertTrue(summaries.isEmpty());
}
```

- [ ] **Step 2: Run test — expect FAIL** (phase runs regardless)

- [ ] **Step 3: Add enabled flag**

```java
private final boolean enabled;

@Inject
public CommunitySummaryPhase(MindMapStore store,
        @ConfigProperty(name = "casehub.mindmap.consolidation.community-summary.enabled",
                         defaultValue = "false") boolean enabled) {
    this(store, 3, 4, 5, enabled);
}

CommunitySummaryPhase(MindMapStore store, int k, int minClusterSize,
                       int maxPerPass, boolean enabled) {
    this.store          = store;
    this.k              = k;
    this.minClusterSize = minClusterSize;
    this.maxPerPass     = maxPerPass;
    this.enabled        = enabled;
}

@Override
public void run(String tenantId, List<String> subgraphPriority) {
    if (!enabled) return;
    // ... existing logic
}
```

- [ ] **Step 4: Run test — expect PASS**

- [ ] **Step 5: Run all CommunitySummaryPhaseTest — expect green**

Update existing tests to pass `enabled=true` to the test constructor.

- [ ] **Step 6: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/CommunitySummaryPhase.java
git add mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/CommunitySummaryPhaseTest.java
git commit -m "fix(mindmap-intelligence): disable CommunitySummaryPhase by default

Gated by casehub.mindmap.consolidation.community-summary.enabled (default
false). No consumer exists for Summary-trait nodes. Infrastructure preserved
for future activation via config change + restart.

Refs #1224"
```

---

## Batch 6: Module build verification

### Task 12: Full module test run + integration verification

**Files:** None (verification only)

- [ ] **Step 1: Run full mindmap-intelligence test suite**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean test -pl mindmap-intelligence
```

Expected: All 134+ existing tests + ~24 new tests pass.

- [ ] **Step 2: Run full reactor build**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -DskipTests
```

Expected: BUILD SUCCESS (no compile errors across modules).

- [ ] **Step 3: Run downstream module tests** (modules that depend on mindmap-intelligence)

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition
```

Expected: All cognition tests pass (mindmap-intelligence changes don't break consumers).

- [ ] **Step 4: Commit any test fixes**

---

## References

- [2026-11-25-consolidation-phase-fixes-design.md] — design spec
- [decisions.md] — 10 decisions from first-principles analysis
- [SubThoughtExtractor.java:49] — entity hardcoded to null (F6)
- [SubThoughtConsolidationPhase.java:87,124] — signals drain + entity skip (F3, F6)
- [CognitiveAttentionAccumulator.java:34,96] — principal-only key (F3)
- [ConsolidationScheduler.java:343-350] — global swap (F5)
- [SignificanceAccumulator.java:97-105] — swapAndReset (F5)
- [ExperienceConsolidationPhase.java:173-179,203,223] — cursor + names (F7)
- [DefaultGraduationClassifier.java] — classification metadata for naming (F7)
- [MergeDetectionPhase.java:140-165] — merge thresholds (F11)
- [GoalResolutionPhase.java:183-238] — goal merge (F11)
- [ConversationBridge.java:62-63] — STATED default (F19)
- [MindMapExtractor.java:418] — fuzzy 0.85 unscoped (F19)
- [CommunitySummaryPhase.java:28-50,169-177] — placeholder text (F16)
- [entity-token/SurfaceFormTrie.java] — Aho-Corasick scanner (D1)
- [GitHub #1224] — SE4 consolidation phase fixes
- [GitHub #1220] — parent epic
