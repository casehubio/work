# Composable Routing + JPAF Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #790 — Personality-adaptive agent routing
**Issue group:** #791, #792, #793, #794, #795, #796

**Goal:** Replace mutually exclusive routing strategies with composable signal providers, then layer JPAF personality-adaptive routing on top.

**Architecture:** Evolve the existing `RoutingSignalProvider` SPI (engine-api) from a supplementary enrichment mechanism to the primary Layer 3 scoring system. Each provider scores candidates independently; a `ComposableAgentRoutingStrategy` blends scores with configurable weights. Existing strategies (`LeastLoaded`, `TrustWeighted`, `Semantic`) decompose into signal providers. A new `PersonalitySignalProvider` scores candidates by cognitive function alignment using eidos personality profiles and JPAF reinforcement signals.

**Tech Stack:** Java 26, Quarkus 3.32.2, casehub-eidos-api 0.2-SNAPSHOT (DispositionSignalStore, DispositionHealth, DispositionEvolution)

## Global Constraints

- All code navigation and editing via IntelliJ MCP (`mcp__intellij-index__*` tools)
- `RoutingSignalProvider` already exists at `io.casehub.api.spi.routing` — evolve, don't create
- `Capability` is in `casehub-worker-api` (foundation tier) — do NOT modify it
- `CognitiveDemand` stored as `Map<String, CognitiveDemand>` on `CaseDefinition`, NOT on `Capability`
- Signal providers are `@ApplicationScoped` beans discovered by `RoutingSignalAssembler` via CDI
- Layer 4 strategies (blocks `LlmAgentRoutingStrategy`, `CbrAgentRoutingStrategy`) remain as `AgentRoutingStrategy` — untouched
- Eidos SPIs (`DispositionSignalStore`, `DispositionHealth`, `DispositionEvolution`) are consumed, not implemented — eidos owns implementations
- Tests: `@QuarkusTest` with `casehub-persistence-memory`, surefire naming (`*Test.java`)
- Build before test: `mvn install -DskipTests -q` then `TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl <module>`

---

### Task 1: Evolve RoutingSignalProvider SPI

**Refs:** NEW-1

**Files:**
- Modify: `api/src/main/java/io/casehub/api/spi/routing/RoutingSignal.java`
- Modify: `api/src/main/java/io/casehub/api/spi/routing/RoutingSignalProvider.java`
- Modify: `api/src/main/java/io/casehub/api/spi/routing/RoutingSignalAssembler.java`
- Modify: `api/src/test/java/io/casehub/api/spi/routing/RoutingSignalAssemblerTest.java`

**Interfaces:**
- Produces: `RoutingSignalProvider.evaluate(AgentRoutingContext, List<AgentCandidate>) → @Nullable RoutingSignal`
- Produces: `CandidateSignal` sealed interface with `Score(double, String)`, `Exclude(String)`, `Escalate(EscalationReason, String)`
- Produces: `RoutingSignalProvider extends NamedStrategy`

- [ ] **Step 1: Write test for CandidateSignal sealed variants**

```java
// api/src/test/java/io/casehub/api/spi/routing/CandidateSignalTest.java
package io.casehub.api.spi.routing;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CandidateSignalTest {

    @Test
    void score_carriesValueAndRationale() {
        var signal = new RoutingSignal.CandidateSignal.Score(0.85, "high trust");
        assertThat(signal.value()).isEqualTo(0.85);
        assertThat(signal.rationale()).isEqualTo("high trust");
    }

    @Test
    void exclude_carriesReason() {
        var signal = new RoutingSignal.CandidateSignal.Exclude("phase 2b");
        assertThat(signal.reason()).isEqualTo("phase 2b");
    }

    @Test
    void escalate_carriesReasonAndRationale() {
        var signal = new RoutingSignal.CandidateSignal.Escalate(
                EscalationReason.NO_QUALIFIED_AGENT, "bootstrap only");
        assertThat(signal.reason()).isEqualTo(EscalationReason.NO_QUALIFIED_AGENT);
        assertThat(signal.rationale()).isEqualTo("bootstrap only");
    }

    @Test
    void sealedSwitch_exhaustive() {
        RoutingSignal.CandidateSignal signal = new RoutingSignal.CandidateSignal.Score(0.5, null);
        String result = switch (signal) {
            case RoutingSignal.CandidateSignal.Score s -> "score:" + s.value();
            case RoutingSignal.CandidateSignal.Exclude e -> "exclude:" + e.reason();
            case RoutingSignal.CandidateSignal.Escalate esc -> "escalate:" + esc.reason();
        };
        assertThat(result).startsWith("score:");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl api -Dtest=CandidateSignalTest -f /Users/mdproctor/claude/casehub/worktrees/49/engine/pom.xml`
Expected: FAIL — `CandidateSignal` is a record, not a sealed interface

- [ ] **Step 3: Evolve CandidateSignal from record to sealed interface**

Use `ide_edit_member` on `RoutingSignal.java` to replace the `CandidateSignal` inner record with:

```java
public sealed interface CandidateSignal {
    record Score(double value, @Nullable String rationale) implements CandidateSignal {}
    record Exclude(String reason) implements CandidateSignal {}
    record Escalate(EscalationReason reason, String rationale) implements CandidateSignal {}
}
```

Remove the old `CandidateSignal` record. The `RoutingSignal` record keeps its `Map<String, CandidateSignal> candidates` field unchanged — the map values are now the sealed interface instead of the record.

- [ ] **Step 4: Evolve RoutingSignalProvider — extend NamedStrategy, rename signal→evaluate**

Use `ide_edit_member` on `RoutingSignalProvider.java`:
- Add `extends NamedStrategy` to the interface declaration (import `io.casehub.platform.api.routing.NamedStrategy`)
- Rename `signal()` method to `evaluate()` using `ide_refactor_rename`
- `id()` method already present — satisfies `NamedStrategy` contract

- [ ] **Step 5: Update RoutingSignalAssembler for sealed CandidateSignal**

Use `ide_replace_member` on `RoutingSignalAssembler.clampScores()`:

```java
private static RoutingSignal clampScores(RoutingSignal signal, String providerId) {
    var clamped = new LinkedHashMap<String, RoutingSignal.CandidateSignal>();
    boolean anyClamped = false;
    for (var entry : signal.candidates().entrySet()) {
        var cs = entry.getValue();
        switch (cs) {
            case RoutingSignal.CandidateSignal.Score s -> {
                double score = s.value();
                if (score < 0.0 || score > 1.0) {
                    anyClamped = true;
                    score = Math.max(0.0, Math.min(1.0, score));
                }
                clamped.put(entry.getKey(), new RoutingSignal.CandidateSignal.Score(score, s.rationale()));
            }
            case RoutingSignal.CandidateSignal.Exclude e -> clamped.put(entry.getKey(), e);
            case RoutingSignal.CandidateSignal.Escalate e -> clamped.put(entry.getKey(), e);
        }
    }
    if (anyClamped) {
        LOG.warnf("RoutingSignalProvider '%s' returned out-of-range scores — clamped to [0,1]", providerId);
        return new RoutingSignal(clamped);
    }
    return signal;
}
```

Also update `assemble()` to call `evaluate()` instead of `signal()`.

- [ ] **Step 6: Update RoutingSignalAssemblerTest**

Update all test helper lambdas from `signal(ctx, eligible)` to `evaluate(ctx, eligible)`. Update `CandidateSignal` construction from `new RoutingSignal.CandidateSignal(score, reason)` to `new RoutingSignal.CandidateSignal.Score(score, reason)`. Add test for Exclude and Escalate pass-through in clamp.

- [ ] **Step 7: Verify with ide_diagnostics and ide_build_project**

Run `ide_diagnostics` on all modified files. Run `ide_build_project`.

- [ ] **Step 8: Run all tests**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl api -f /Users/mdproctor/claude/casehub/worktrees/49/engine/pom.xml`
Expected: ALL PASS

- [ ] **Step 9: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/worktrees/49/engine add api/src/main/java/io/casehub/api/spi/routing/RoutingSignal.java api/src/main/java/io/casehub/api/spi/routing/RoutingSignalProvider.java api/src/main/java/io/casehub/api/spi/routing/RoutingSignalAssembler.java api/src/test/java/io/casehub/api/spi/routing/RoutingSignalAssemblerTest.java api/src/test/java/io/casehub/api/spi/routing/CandidateSignalTest.java
git -C /Users/mdproctor/claude/casehub/worktrees/49/engine commit -m "feat(#790): evolve RoutingSignalProvider — sealed CandidateSignal, NamedStrategy, evaluate()"
```

---

### Task 2: ComposableAgentRoutingStrategy

**Refs:** NEW-2

**Files:**
- Create: `runtime/src/main/java/io/casehub/engine/internal/routing/ComposableAgentRoutingStrategy.java`
- Create: `runtime/src/test/java/io/casehub/engine/internal/routing/ComposableAgentRoutingStrategyTest.java`

**Interfaces:**
- Consumes: `RoutingSignalAssembler.assemble(context, eligible) → Map<String, RoutingSignal>`
- Consumes: `RoutingSignal.CandidateSignal` sealed: `Score`, `Exclude`, `Escalate`
- Consumes: `AgentRoutingContext.routingSignalWeights()` (added in Task 6 — use null until then)
- Produces: `AgentRoutingStrategy` with id=`"composable"`, `@DefaultBean @ApplicationScoped @Unremovable`

- [ ] **Step 1: Write tests for the compositor**

```java
// runtime/src/test/java/io/casehub/engine/internal/routing/ComposableAgentRoutingStrategyTest.java
package io.casehub.engine.internal.routing;

import static org.assertj.core.api.Assertions.*;

import io.casehub.api.spi.routing.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ComposableAgentRoutingStrategyTest {

    @Test
    void singleProvider_selectsHighestScore() {
        var provider = testProvider("workload", Map.of(
                "agent-a", new RoutingSignal.CandidateSignal.Score(0.8, "low load"),
                "agent-b", new RoutingSignal.CandidateSignal.Score(0.3, "high load")));
        var strategy = new ComposableAgentRoutingStrategy(
                new RoutingSignalAssembler(List.of(provider)));

        var candidates = List.of(candidate("agent-a"), candidate("agent-b"));
        var ctx = testContext(null);
        var result = strategy.select(ctx, candidates);

        assertThat(result).isInstanceOf(RoutingResult.Selected.class);
        assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-a");
    }

    @Test
    void excludeRemovesCandidate() {
        var provider = testProvider("trust", Map.of(
                "agent-a", new RoutingSignal.CandidateSignal.Score(0.9, "qualified"),
                "agent-b", new RoutingSignal.CandidateSignal.Exclude("phase 2b")));
        var strategy = new ComposableAgentRoutingStrategy(
                new RoutingSignalAssembler(List.of(provider)));

        var candidates = List.of(candidate("agent-a"), candidate("agent-b"));
        var result = strategy.select(testContext(null), candidates);

        assertThat(result).isInstanceOf(RoutingResult.Selected.class);
        assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-a");
    }

    @Test
    void allExcluded_returnsUnresolvable() {
        var provider = testProvider("trust", Map.of(
                "agent-a", new RoutingSignal.CandidateSignal.Exclude("phase 2b")));
        var strategy = new ComposableAgentRoutingStrategy(
                new RoutingSignalAssembler(List.of(provider)));

        var result = strategy.select(testContext(null), List.of(candidate("agent-a")));

        assertThat(result).isInstanceOf(RoutingResult.Unresolvable.class);
    }

    @Test
    void escalateSignal_producesEscalatedResult() {
        var provider = testProvider("trust", Map.of(
                "agent-a", new RoutingSignal.CandidateSignal.Escalate(
                        EscalationReason.NO_QUALIFIED_AGENT, "bootstrap only")));
        var strategy = new ComposableAgentRoutingStrategy(
                new RoutingSignalAssembler(List.of(provider)));

        var result = strategy.select(testContext(null), List.of(candidate("agent-a")));

        assertThat(result).isInstanceOf(RoutingResult.Escalated.class);
    }

    @Test
    void weightedBlending_twoProviders() {
        var p1 = testProvider("trust", Map.of(
                "agent-a", new RoutingSignal.CandidateSignal.Score(0.2, null),
                "agent-b", new RoutingSignal.CandidateSignal.Score(0.9, null)));
        var p2 = testProvider("workload", Map.of(
                "agent-a", new RoutingSignal.CandidateSignal.Score(1.0, null),
                "agent-b", new RoutingSignal.CandidateSignal.Score(0.1, null)));
        var strategy = new ComposableAgentRoutingStrategy(
                new RoutingSignalAssembler(List.of(p1, p2)));

        // Equal weights: agent-a = (0.2+1.0)/2 = 0.6, agent-b = (0.9+0.1)/2 = 0.5
        var result = strategy.select(testContext(null), List.of(candidate("agent-a"), candidate("agent-b")));

        assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-a");
    }

    @Test
    void absentCandidate_weightRedistributed() {
        // p1 scores both, p2 only scores agent-b
        var p1 = testProvider("trust", Map.of(
                "agent-a", new RoutingSignal.CandidateSignal.Score(0.8, null),
                "agent-b", new RoutingSignal.CandidateSignal.Score(0.7, null)));
        var p2 = testProvider("personality", Map.of(
                "agent-b", new RoutingSignal.CandidateSignal.Score(0.9, null)));
        var strategy = new ComposableAgentRoutingStrategy(
                new RoutingSignalAssembler(List.of(p1, p2)));

        // agent-a: only trust scores (weight 1.0 after redistribution) = 0.8
        // agent-b: trust 0.5 + personality 0.5 = 0.7*0.5 + 0.9*0.5 = 0.8
        // Tie — first wins (agent-a by list order)
        var result = strategy.select(testContext(null), List.of(candidate("agent-a"), candidate("agent-b")));
        assertThat(result).isInstanceOf(RoutingResult.Selected.class);
    }

    @Test
    void allAbstain_neutralScore() {
        // Provider returns null (abstain) — assembler skips it
        var provider = new RoutingSignalProvider() {
            @Override public String id() { return "empty"; }
            @Override public RoutingSignal evaluate(AgentRoutingContext ctx, java.util.List<AgentCandidate> eligible) {
                return null;
            }
        };
        var strategy = new ComposableAgentRoutingStrategy(
                new RoutingSignalAssembler(List.of(provider)));

        var result = strategy.select(testContext(null), List.of(candidate("agent-a")));
        assertThat(result).isInstanceOf(RoutingResult.Selected.class);
    }

    @Test
    void perCaseWeights_onlyNamedProvidersUsed() {
        var p1 = testProvider("trust", Map.of(
                "agent-a", new RoutingSignal.CandidateSignal.Score(0.1, null)));
        var p2 = testProvider("workload", Map.of(
                "agent-a", new RoutingSignal.CandidateSignal.Score(0.9, null)));
        var strategy = new ComposableAgentRoutingStrategy(
                new RoutingSignalAssembler(List.of(p1, p2)));

        // Only workload with weight 1.0 — trust not included
        var weights = Map.of("workload", 1.0);
        var result = strategy.select(testContext(weights), List.of(candidate("agent-a")));

        assertThat(result).isInstanceOf(RoutingResult.Selected.class);
    }

    @Test
    void emptyCandidates_returnsUnresolvable() {
        var strategy = new ComposableAgentRoutingStrategy(
                new RoutingSignalAssembler(List.of()));
        var result = strategy.select(testContext(null), List.of());
        assertThat(result).isInstanceOf(RoutingResult.Unresolvable.class);
    }

    // --- helpers ---

    private static RoutingSignalProvider testProvider(String id, Map<String, RoutingSignal.CandidateSignal> signals) {
        return new RoutingSignalProvider() {
            @Override public String id() { return id; }
            @Override public RoutingSignal evaluate(AgentRoutingContext ctx, java.util.List<AgentCandidate> eligible) {
                return new RoutingSignal(signals);
            }
        };
    }

    private static AgentCandidate candidate(String workerId) {
        return new AgentCandidate(workerId, Set.of(), 0, AgentHealth.READY, null, null);
    }

    private static AgentRoutingContext testContext(Map<String, Double> weights) {
        return new AgentRoutingContext(
                java.util.UUID.randomUUID(), "test-capability",
                com.fasterxml.jackson.databind.node.NullNode.getInstance(),
                "tenant-1", List.of(), null, weights);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn install -DskipTests -q -f /Users/mdproctor/claude/casehub/worktrees/49/engine/pom.xml`
Then: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl runtime -Dtest=ComposableAgentRoutingStrategyTest -f /Users/mdproctor/claude/casehub/worktrees/49/engine/pom.xml`
Expected: FAIL — class not found

- [ ] **Step 3: Implement ComposableAgentRoutingStrategy**

Create via `ide_create_file`:

```java
package io.casehub.engine.internal.routing;

import io.casehub.api.spi.routing.*;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;
import org.jboss.logging.Logger;

@DefaultBean
@ApplicationScoped
@Unremovable
public class ComposableAgentRoutingStrategy implements AgentRoutingStrategy {

    private static final Logger LOG = Logger.getLogger(ComposableAgentRoutingStrategy.class);
    private final RoutingSignalAssembler assembler;

    @Inject
    public ComposableAgentRoutingStrategy(RoutingSignalAssembler assembler) {
        this.assembler = assembler;
    }

    @Override
    public String id() {
        return "composable";
    }

    @Override
    public RoutingResult select(AgentRoutingContext context, List<AgentCandidate> candidates) {
        if (candidates.isEmpty()) {
            return RoutingResult.unresolvable("no candidates available");
        }

        Map<String, RoutingSignal> allSignals = assembler.assemble(context, candidates);
        Map<String, Double> weights = resolveWeights(context, allSignals.keySet());

        Map<String, Double> scores = new LinkedHashMap<>();
        List<String> excludedReasons = new ArrayList<>();
        EscalationReason escalationReason = null;
        String escalationRationale = null;

        for (AgentCandidate candidate : candidates) {
            String workerId = candidate.workerId();
            boolean excluded = false;

            Map<String, Double> candidateScores = new LinkedHashMap<>();
            double totalWeight = 0.0;

            for (var entry : weights.entrySet()) {
                String providerId = entry.getKey();
                double weight = entry.getValue();
                RoutingSignal signal = allSignals.get(providerId);
                if (signal == null) continue;

                RoutingSignal.CandidateSignal cs = signal.candidates().get(workerId);
                if (cs == null) continue;

                switch (cs) {
                    case RoutingSignal.CandidateSignal.Score s -> {
                        candidateScores.put(providerId, s.value());
                        totalWeight += weight;
                    }
                    case RoutingSignal.CandidateSignal.Exclude e -> {
                        excluded = true;
                        excludedReasons.add(workerId + ": " + e.reason());
                    }
                    case RoutingSignal.CandidateSignal.Escalate e -> {
                        excluded = true;
                        escalationReason = e.reason();
                        escalationRationale = e.rationale();
                    }
                }
                if (excluded) break;
            }

            if (excluded) continue;

            if (candidateScores.isEmpty()) {
                scores.put(workerId, 0.5);
            } else {
                double blended = 0.0;
                for (var entry : candidateScores.entrySet()) {
                    double normalizedWeight = weights.get(entry.getKey()) / totalWeight;
                    blended += entry.getValue() * normalizedWeight;
                }
                scores.put(workerId, blended);
            }
        }

        if (scores.isEmpty()) {
            if (escalationReason != null) {
                return RoutingResult.escalate(context.capabilityName(), escalationReason, escalationRationale);
            }
            return RoutingResult.unresolvable(String.join("; ", excludedReasons));
        }

        String bestWorkerId = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow();
        double bestScore = scores.get(bestWorkerId);

        return RoutingResult.assigned(bestWorkerId,
                "composable score %.3f from %d providers".formatted(bestScore, weights.size()));
    }

    private Map<String, Double> resolveWeights(AgentRoutingContext context, Set<String> discoveredProviders) {
        Map<String, Double> perCase = context.routingSignalWeights();
        if (perCase != null && !perCase.isEmpty()) {
            return perCase;
        }
        Map<String, Double> equal = new LinkedHashMap<>();
        double w = discoveredProviders.isEmpty() ? 1.0 : 1.0 / discoveredProviders.size();
        for (String id : discoveredProviders) {
            equal.put(id, w);
        }
        return equal;
    }
}
```

Note: `AgentRoutingContext` doesn't have `routingSignalWeights()` yet — that's added in Task 6. For now, use a temporary approach: add the field to `AgentRoutingContext` first (Task 6 formalizes it with YAML), or test with null. The test helper `testContext(weights)` constructs a context with the new field.

- [ ] **Step 4: Add routingSignalWeights to AgentRoutingContext (structural prerequisite)**

Use `ide_edit_member` on `AgentRoutingContext.java` to add two new record components:

```java
public record AgentRoutingContext(
    UUID caseId,
    String capabilityName,
    JsonNode caseContext,
    String tenancyId,
    List<RetrievedExperience> experiences,
    @Nullable CognitiveDemand cognitiveDemand,
    @Nullable Map<String, Double> routingSignalWeights) {}
```

`CognitiveDemand` doesn't exist yet — use a forward reference or create a stub record in the same package. Better: create the `CognitiveDemand` record now (it's tiny and Task 6 needs it):

```java
// api/src/main/java/io/casehub/api/model/CognitiveDemand.java
package io.casehub.api.model;

import java.util.Map;

public record CognitiveDemand(Map<String, Double> functionWeights) {
    public CognitiveDemand {
        functionWeights = Map.copyOf(functionWeights);
        double sum = functionWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 1.0) > 0.01) {
            throw new IllegalArgumentException("functionWeights must sum to 1.0, got " + sum);
        }
    }
}
```

Fix all existing call sites of `AgentRoutingContext` constructor — add `null, null` for the two new fields. Use `ide_find_references` on the `AgentRoutingContext` constructor to find all call sites.

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn install -DskipTests -q -f /Users/mdproctor/claude/casehub/worktrees/49/engine/pom.xml`
Then: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl runtime -Dtest=ComposableAgentRoutingStrategyTest -f /Users/mdproctor/claude/casehub/worktrees/49/engine/pom.xml`
Expected: ALL PASS

- [ ] **Step 6: Verify with ide_build_project**

Run `ide_build_project` to confirm no compilation errors across the full project.

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/worktrees/49/engine add api/src/main/java/io/casehub/api/spi/routing/AgentRoutingContext.java api/src/main/java/io/casehub/api/model/CognitiveDemand.java runtime/src/main/java/io/casehub/engine/internal/routing/ComposableAgentRoutingStrategy.java runtime/src/test/java/io/casehub/engine/internal/routing/ComposableAgentRoutingStrategyTest.java
git -C /Users/mdproctor/claude/casehub/worktrees/49/engine commit -m "feat(#790): ComposableAgentRoutingStrategy — weighted signal compositor"
```

---

### Task 3: WorkloadSignalProvider + ExperienceSignalProvider

**Refs:** NEW-3

**Files:**
- Create: `runtime/src/main/java/io/casehub/engine/internal/routing/WorkloadSignalProvider.java`
- Create: `runtime/src/main/java/io/casehub/engine/internal/routing/ExperienceSignalProvider.java`
- Create: `runtime/src/test/java/io/casehub/engine/internal/routing/WorkloadSignalProviderTest.java`
- Create: `runtime/src/test/java/io/casehub/engine/internal/routing/ExperienceSignalProviderTest.java`

**Interfaces:**
- Consumes: `RoutingSignalProvider.evaluate()`, `RoutingSignal.CandidateSignal.Score`
- Consumes: `ExperienceAnalyser.workerSuccessRates()` (existing, `api/spi/routing/`)
- Produces: `WorkloadSignalProvider` (id=`"workload"`, `@ApplicationScoped`)
- Produces: `ExperienceSignalProvider` (id=`"experience"`, `@ApplicationScoped`)

- [ ] **Step 1: Write WorkloadSignalProvider test**

```java
package io.casehub.engine.internal.routing;

import static org.assertj.core.api.Assertions.*;

import io.casehub.api.spi.routing.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WorkloadSignalProviderTest {

    private final WorkloadSignalProvider provider = new WorkloadSignalProvider();

    @Test
    void id() {
        assertThat(provider.id()).isEqualTo("workload");
    }

    @Test
    void zeroJobs_scoresOne() {
        var candidates = List.of(candidate("a", 0));
        var result = provider.evaluate(testContext(), candidates);
        assertThat(result).isNotNull();
        var signal = result.candidates().get("a");
        assertThat(signal).isInstanceOf(RoutingSignal.CandidateSignal.Score.class);
        assertThat(((RoutingSignal.CandidateSignal.Score) signal).value()).isEqualTo(1.0);
    }

    @Test
    void moreJobs_lowerScore() {
        var candidates = List.of(candidate("a", 0), candidate("b", 3));
        var result = provider.evaluate(testContext(), candidates);
        var scoreA = ((RoutingSignal.CandidateSignal.Score) result.candidates().get("a")).value();
        var scoreB = ((RoutingSignal.CandidateSignal.Score) result.candidates().get("b")).value();
        assertThat(scoreA).isGreaterThan(scoreB);
        assertThat(scoreB).isCloseTo(0.25, within(0.001));
    }

    private static AgentCandidate candidate(String id, int jobs) {
        return new AgentCandidate(id, Set.of(), jobs, AgentHealth.READY, null, null);
    }

    private static AgentRoutingContext testContext() {
        return new AgentRoutingContext(UUID.randomUUID(), "cap",
                com.fasterxml.jackson.databind.node.NullNode.getInstance(),
                "t1", List.of(), null, null);
    }
}
```

- [ ] **Step 2: Implement WorkloadSignalProvider**

```java
package io.casehub.engine.internal.routing;

import io.casehub.api.spi.routing.*;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.List;

@ApplicationScoped
public class WorkloadSignalProvider implements RoutingSignalProvider {

    @Override
    public String id() {
        return "workload";
    }

    @Override
    public RoutingSignal evaluate(AgentRoutingContext context, List<AgentCandidate> eligible) {
        var signals = new LinkedHashMap<String, RoutingSignal.CandidateSignal>();
        for (var candidate : eligible) {
            double score = 1.0 / (1.0 + candidate.runningJobs());
            signals.put(candidate.workerId(),
                    new RoutingSignal.CandidateSignal.Score(score,
                            "load %d".formatted(candidate.runningJobs())));
        }
        return new RoutingSignal(signals);
    }
}
```

- [ ] **Step 3: Write ExperienceSignalProvider test**

```java
package io.casehub.engine.internal.routing;

import static org.assertj.core.api.Assertions.*;

import io.casehub.api.spi.routing.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ExperienceSignalProviderTest {

    private final ExperienceSignalProvider provider = new ExperienceSignalProvider();

    @Test
    void id() {
        assertThat(provider.id()).isEqualTo("experience");
    }

    @Test
    void noExperiences_returnsNull() {
        var result = provider.evaluate(testContext(List.of()), List.of(candidate("a")));
        assertThat(result).isNull();
    }

    // Integration tests with real ExperienceAnalyser require RetrievedExperience fixtures —
    // covered in existing ExperienceAnalyserTest. Provider just wraps the analyser call.

    private static AgentCandidate candidate(String id) {
        return new AgentCandidate(id, Set.of(), 0, AgentHealth.READY, null, null);
    }

    private static AgentRoutingContext testContext(List<RetrievedExperience> experiences) {
        return new AgentRoutingContext(UUID.randomUUID(), "cap",
                com.fasterxml.jackson.databind.node.NullNode.getInstance(),
                "t1", experiences, null, null);
    }
}
```

- [ ] **Step 4: Implement ExperienceSignalProvider**

```java
package io.casehub.engine.internal.routing;

import io.casehub.api.spi.routing.*;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
public class ExperienceSignalProvider implements RoutingSignalProvider {

    @Override
    public String id() {
        return "experience";
    }

    @Override
    public @Nullable RoutingSignal evaluate(AgentRoutingContext context, List<AgentCandidate> eligible) {
        if (context.experiences() == null || context.experiences().isEmpty()) {
            return null;
        }
        Set<String> workerIds = eligible.stream().map(AgentCandidate::workerId).collect(Collectors.toSet());
        Map<String, Double> rates = ExperienceAnalyser.workerSuccessRates(
                context.experiences(), workerIds, context.capabilityName(),
                ExperienceAnalyser.DEFAULT_OUTCOME_WEIGHTS);
        if (rates.isEmpty()) {
            return null;
        }
        var signals = new LinkedHashMap<String, RoutingSignal.CandidateSignal>();
        for (var entry : rates.entrySet()) {
            signals.put(entry.getKey(),
                    new RoutingSignal.CandidateSignal.Score(entry.getValue(),
                            "experience rate %.2f".formatted(entry.getValue())));
        }
        return new RoutingSignal(signals);
    }
}
```

- [ ] **Step 5: Run tests, verify, commit**

Run: `mvn install -DskipTests -q -f /Users/mdproctor/claude/casehub/worktrees/49/engine/pom.xml`
Then: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl runtime -Dtest="WorkloadSignalProviderTest,ExperienceSignalProviderTest" -f /Users/mdproctor/claude/casehub/worktrees/49/engine/pom.xml`

```bash
git -C /Users/mdproctor/claude/casehub/worktrees/49/engine add runtime/src/main/java/io/casehub/engine/internal/routing/WorkloadSignalProvider.java runtime/src/main/java/io/casehub/engine/internal/routing/ExperienceSignalProvider.java runtime/src/test/java/io/casehub/engine/internal/routing/WorkloadSignalProviderTest.java runtime/src/test/java/io/casehub/engine/internal/routing/ExperienceSignalProviderTest.java
git -C /Users/mdproctor/claude/casehub/worktrees/49/engine commit -m "feat(#790): WorkloadSignalProvider + ExperienceSignalProvider"
```

---

### Task 4: TrustSignalProvider

**Refs:** NEW-4

**Files:**
- Create: `ledger/src/main/java/io/casehub/ledger/routing/TrustSignalProvider.java`
- Create: `ledger/src/test/java/io/casehub/ledger/routing/TrustSignalProviderTest.java`

**Interfaces:**
- Consumes: `TrustCandidateClassifier`, `TrustScoreSource`, `TrustRoutingPolicyProvider` (all existing in ledger)
- Consumes: `RoutingSignal.CandidateSignal.Score`, `Exclude`, `Escalate`
- Produces: `TrustSignalProvider` (id=`"trust"`, `@ApplicationScoped`)

- [ ] **Step 1: Write TrustSignalProvider test**

Test the three trust phases: QUALIFIED → Score, BOOTSTRAP → Score(availability), BORDERLINE/EXCLUDED → Exclude, and the all-borderline → Escalate case.

```java
package io.casehub.ledger.routing;

import static org.assertj.core.api.Assertions.*;

import io.casehub.api.spi.routing.*;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TrustSignalProviderTest {

    @Test
    void id() {
        var provider = createProvider();
        assertThat(provider.id()).isEqualTo("trust");
    }

    @Test
    void qualifiedCandidate_returnsScore() {
        // Test via the full classify→score pipeline
        // This requires setting up TrustScoreSource and TrustRoutingPolicyProvider mocks
        // Follow existing TrustWeightedAgentStrategyTest patterns
    }

    @Test
    void excludedCandidate_returnsExclude() {
        // Phase 2b/3 candidates → Exclude signal
    }

    @Test
    void allBorderline_returnsEscalate() {
        // All candidates BORDERLINE → Escalate with BORDERLINE_STALEMATE
    }

    @Test
    void bootstrapOnly_withPolicy_returnsEscalate() {
        // bootstrapEscalationRequired=true, only bootstrap → Escalate with NO_QUALIFIED_AGENT
    }

    private TrustSignalProvider createProvider() {
        // Wire with test doubles following existing TrustWeightedAgentStrategyTest patterns
        return null; // placeholder — implement during execution
    }
}
```

Note: Full test code follows the existing `TrustWeightedAgentStrategyTest` patterns. The logic is the same — the refactoring moves scoring from `AgentRoutingStrategy.select() → RoutingResult` to `RoutingSignalProvider.evaluate() → RoutingSignal`.

- [ ] **Step 2: Implement TrustSignalProvider**

Extract the classification and scoring logic from `TrustWeightedAgentStrategy.select()` into `TrustSignalProvider.evaluate()`. The provider:
1. Classifies candidates via `TrustCandidateClassifier.classify()`
2. For QUALIFIED: returns `Score(blendedTrustAndWorkload)`
3. For BOOTSTRAP: returns `Score(availabilityScore)`
4. For BORDERLINE/EXCLUDED: returns `Exclude(reason)`
5. Cross-candidate check: if all non-bootstrap are borderline → Escalate

Note: the trust-workload blend factor (`policy.blendFactor()`) stays inside the trust provider. The provider blends trust with its OWN workload assessment internally — this is trust-specific behavior, not compositor behavior. The compositor's workload provider contributes a separate workload signal.

Wait — this means workload would be double-counted (once inside trust, once via WorkloadSignalProvider). The refactoring should REMOVE the internal workload blend from trust. TrustSignalProvider returns pure trust scores. The compositor blends trust and workload at the top level.

Revised: TrustSignalProvider returns PURE trust scores:
- QUALIFIED: `Score(trustScore)` — the raw trust maturity score
- BOOTSTRAP: `Score(0.5)` — neutral (no trust data)
- BORDERLINE/EXCLUDED: `Exclude`/`Escalate`

The workload blend is handled by the compositor (WorkloadSignalProvider contributes separately).

- [ ] **Step 3: Run tests, verify, commit**

Run: `mvn install -DskipTests -q -f /Users/mdproctor/claude/casehub/worktrees/49/engine/pom.xml`
Then: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl ledger -Dtest=TrustSignalProviderTest -f /Users/mdproctor/claude/casehub/worktrees/49/engine/pom.xml`

```bash
git -C /Users/mdproctor/claude/casehub/worktrees/49/engine add ledger/src/main/java/io/casehub/ledger/routing/TrustSignalProvider.java ledger/src/test/java/io/casehub/ledger/routing/TrustSignalProviderTest.java
git -C /Users/mdproctor/claude/casehub/worktrees/49/engine commit -m "feat(#790): TrustSignalProvider — trust scoring as composable signal"
```

---

### Task 5: SemanticSignalProvider

**Refs:** NEW-5

**Files:**
- Create: `engine-ai/src/main/java/io/casehub/engine/ai/routing/SemanticSignalProvider.java`
- Create: `engine-ai/src/test/java/io/casehub/engine/ai/routing/SemanticSignalProviderTest.java`

**Interfaces:**
- Consumes: Embedding service from `SemanticAgentRoutingStrategy` (existing internal dependency)
- Produces: `SemanticSignalProvider` (id=`"semantic"`, `@ApplicationScoped`)

- [ ] **Step 1: Extract scoring logic from SemanticAgentRoutingStrategy**

Read `SemanticAgentRoutingStrategy` via `ide_read_file`. Extract the embedding + similarity scoring into `SemanticSignalProvider.evaluate()`. Returns `Score(similarity)` for each candidate, `null` (abstain) when embedding service unavailable.

- [ ] **Step 2: Write tests following existing SemanticAgentRoutingStrategyTest patterns**

- [ ] **Step 3: Run tests, verify, commit**

```bash
git -C /Users/mdproctor/claude/casehub/worktrees/49/engine add engine-ai/src/main/java/io/casehub/engine/ai/routing/SemanticSignalProvider.java engine-ai/src/test/java/io/casehub/engine/ai/routing/SemanticSignalProviderTest.java
git -C /Users/mdproctor/claude/casehub/worktrees/49/engine commit -m "feat(#790): SemanticSignalProvider — embedding similarity as composable signal"
```

---

### Task 6: CaseDefinition routing config + YAML mapping

**Refs:** NEW-6

**Files:**
- Modify: `api/src/main/java/io/casehub/api/model/CaseDefinition.java` — add `routingSignalWeights`, `cognitiveDemands`
- Modify: `api/src/main/java/io/casehub/api/model/converter/CaseDefinitionYamlMapper.java` — parse `routingSignalWeights:` and `cognitiveDemand:` from YAML
- Create: `api/src/test/java/io/casehub/api/model/converter/CaseDefinitionYamlMapperRoutingTest.java`

**Interfaces:**
- Consumes: `CognitiveDemand` record (created in Task 2)
- Produces: `CaseDefinition.getRoutingSignalWeights() → Map<String, Double>` (nullable)
- Produces: `CaseDefinition.getCognitiveDemand(String capabilityName) → CognitiveDemand` (nullable)
- Produces: `CaseDefinition.Builder.routingSignalWeights(Map<String, Double>)`
- Produces: `CaseDefinition.Builder.cognitiveDemand(String capabilityName, CognitiveDemand demand)`

- [ ] **Step 1: Write YAML mapping test**

```java
package io.casehub.api.model.converter;

import static org.assertj.core.api.Assertions.*;

import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.CognitiveDemand;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CaseDefinitionYamlMapperRoutingTest {

    @Test
    void parsesRoutingSignalWeights() {
        String yaml = """
                name: test-case
                namespace: test
                routingSignalWeights:
                  trust: 0.4
                  personality: 0.3
                  workload: 0.2
                  experience: 0.1
                """;
        CaseDefinition def = CaseDefinitionYamlMapper.fromYaml(yaml);
        assertThat(def.getRoutingSignalWeights())
                .containsEntry("trust", 0.4)
                .containsEntry("personality", 0.3)
                .containsEntry("workload", 0.2)
                .containsEntry("experience", 0.1);
    }

    @Test
    void parsesCognitiveDemandOnCapability() {
        String yaml = """
                name: test-case
                namespace: test
                capabilities:
                  - name: code-review
                    cognitiveDemand:
                      Ti: 0.6
                      Ne: 0.3
                      Si: 0.1
                """;
        CaseDefinition def = CaseDefinitionYamlMapper.fromYaml(yaml);
        CognitiveDemand demand = def.getCognitiveDemand("code-review");
        assertThat(demand).isNotNull();
        assertThat(demand.functionWeights())
                .containsEntry("Ti", 0.6)
                .containsEntry("Ne", 0.3)
                .containsEntry("Si", 0.1);
    }

    @Test
    void missingCognitiveDemand_returnsNull() {
        String yaml = """
                name: test-case
                namespace: test
                capabilities:
                  - name: code-review
                """;
        CaseDefinition def = CaseDefinitionYamlMapper.fromYaml(yaml);
        assertThat(def.getCognitiveDemand("code-review")).isNull();
    }

    @Test
    void missingRoutingSignalWeights_returnsNull() {
        String yaml = """
                name: test-case
                namespace: test
                """;
        CaseDefinition def = CaseDefinitionYamlMapper.fromYaml(yaml);
        assertThat(def.getRoutingSignalWeights()).isNull();
    }
}
```

- [ ] **Step 2: Add fields to CaseDefinition**

Use `ide_insert_member` to add after the `inboundMappings` field:

```java
private Map<String, Double> routingSignalWeights;
private Map<String, CognitiveDemand> cognitiveDemands = Map.of();
```

Add getters, setters, builder methods, and `getCognitiveDemand(String capabilityName)` convenience method.

- [ ] **Step 3: Add YAML parsing to CaseDefinitionYamlMapper**

Use `ide_read_file` on the mapper, then add parsing for:
- `routingSignalWeights:` top-level map → `definition.setRoutingSignalWeights()`
- `cognitiveDemand:` nested under each capability → `definition.setCognitiveDemands()` collected from capability nodes

- [ ] **Step 4: Run tests, verify, commit**

```bash
git -C /Users/mdproctor/claude/casehub/worktrees/49/engine commit -m "feat(#790): CaseDefinition routing config — routingSignalWeights + cognitiveDemands + YAML"
```

---

### Task 7: Wire compositor + delete old strategies

**Refs:** NEW-7

**Files:**
- Modify: `runtime/src/main/java/io/casehub/engine/internal/engine/handler/CaseContextChangedEventHandler.java` — construct `AgentRoutingContext` with new fields
- Delete: `runtime/src/main/java/io/casehub/engine/internal/routing/LeastLoadedAgentStrategy.java` (use `ide_refactor_safe_delete`)
- Delete: `ledger/src/main/java/io/casehub/ledger/routing/TrustWeightedAgentStrategy.java` (use `ide_refactor_safe_delete`)
- Delete: `engine-ai/src/main/java/io/casehub/engine/ai/routing/SemanticAgentRoutingStrategy.java` (use `ide_refactor_safe_delete`)
- Modify: ADR-0003 — mark as Superseded

**Interfaces:**
- Consumes: `ComposableAgentRoutingStrategy` (Task 2), all signal providers (Tasks 3-5)
- Produces: Full compositor wiring in the dispatch path

- [ ] **Step 1: Update CaseContextChangedEventHandler.publishWorkerSchedule()**

Use `ide_read_file` to read the method. The `AgentRoutingContext` construction (around line 420-425) needs the two new fields:

```java
final AgentRoutingContext ctx = new AgentRoutingContext(
    caseInstance.getUuid(),
    capability.name(),
    caseInstance.getCaseContext().layer(ContextLayer.WORKING).asJsonNode(),
    caseInstance.tenancyId,
    experiences,
    caseDefinition.getCognitiveDemand(capability.name()),
    caseDefinition.getRoutingSignalWeights());
```

- [ ] **Step 2: Delete LeastLoadedAgentStrategy**

Use `ide_refactor_safe_delete` on `LeastLoadedAgentStrategy.java`. If usages exist (tests, injections), update them to use `ComposableAgentRoutingStrategy` or remove them.

- [ ] **Step 3: Delete TrustWeightedAgentStrategy**

Use `ide_refactor_safe_delete`. Update any tests that directly reference it.

- [ ] **Step 4: Delete SemanticAgentRoutingStrategy**

Use `ide_refactor_safe_delete`. Update any tests that directly reference it.

- [ ] **Step 5: Update blocks repo — signal() → evaluate()**

Open blocks workspace. Use `ide_find_references` on `RoutingSignalProvider.evaluate` (formerly `signal`) to find all call sites in blocks. `CbrAgentRoutingStrategy` and any other blocks code that calls `RoutingSignalAssembler.assemble()` will see the renamed method automatically (assembler calls `evaluate()` internally). But any blocks code that directly calls `provider.signal()` on a `RoutingSignalProvider` reference needs updating to `provider.evaluate()`.

- [ ] **Step 6: Mark ADR-0003 as Superseded**

Read `docs/adr/` to find ADR-0003. Add `Status: Superseded` with rationale that virtual threads removed the reactive requirement.

- [ ] **Step 7: Run full test suite**

Run: `mvn install -DskipTests -q -f /Users/mdproctor/claude/casehub/worktrees/49/engine/pom.xml`
Then: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -f /Users/mdproctor/claude/casehub/worktrees/49/engine/pom.xml`
Expected: ALL PASS across all modules

- [ ] **Step 8: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/worktrees/49/engine commit -m "feat(#790): wire ComposableAgentRoutingStrategy, delete old strategies, supersede ADR-0003"
```

---

### Task 8: PersonalitySignalRecorder — signal recording

**Refs:** #791

**Files:**
- Create: `runtime/src/main/java/io/casehub/engine/internal/routing/PersonalitySignalRecorder.java`
- Modify: `runtime/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java` — inject and call recorder
- Create: `runtime/src/test/java/io/casehub/engine/internal/routing/PersonalitySignalRecorderTest.java`

**Interfaces:**
- Consumes: `DispositionSignalStore.recordActivation(agentId, tenancyId, functionTerm)` (eidos)
- Consumes: `CaseDefinitionRegistry.findByIdentity()` → `CaseDefinition.agentDescriptorFor()` → `AgentDescriptor.disposition().dispositionProfile()`
- Consumes: `CaseDefinition.getCognitiveDemand(capabilityName)` (Task 6)
- Produces: `PersonalitySignalRecorder.record(CaseInstance, String workerName, String capabilityName, WorkerOutcome)` — called by WorkflowExecutionCompletedHandler

- [ ] **Step 1: Write test for signal attribution on SUCCESS**

```java
package io.casehub.engine.internal.routing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.CognitiveDemand;
import io.casehub.eidos.api.*;
import io.casehub.worker.api.WorkerOutcome;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PersonalitySignalRecorderTest {

    private DispositionSignalStore signalStore;
    private PersonalitySignalRecorder recorder;

    @BeforeEach
    void setUp() {
        signalStore = mock(DispositionSignalStore.class);
        recorder = new PersonalitySignalRecorder(signalStore);
    }

    @Test
    void success_recordsEngagedFunction_dominantMatchesDemand() {
        // Agent: dominant=Ti(0.5), auxiliary=Ne(0.3)
        // Demand: Ti=0.6, Ne=0.3, Si=0.1
        // Ti has higher demand → record Ti
        var profile = List.of(
                new DispositionValue("Ti", 0.5),
                new DispositionValue("Ne", 0.3));
        var demand = new CognitiveDemand(Map.of("Ti", 0.6, "Ne", 0.3, "Si", 0.1));

        recorder.recordSignal("agent-1", "tenant-1", profile, demand, WorkerOutcome.Success.class);

        verify(signalStore).recordActivation("agent-1", "tenant-1", "Ti");
    }

    @Test
    void success_recordsEngagedFunction_auxiliaryMatchesDemand() {
        // Agent: dominant=Ti(0.5), auxiliary=Ne(0.3)
        // Demand: Ne=0.7, Ti=0.2, Si=0.1
        // Ne has higher demand → record Ne
        var profile = List.of(
                new DispositionValue("Ti", 0.5),
                new DispositionValue("Ne", 0.3));
        var demand = new CognitiveDemand(Map.of("Ne", 0.7, "Ti", 0.2, "Si", 0.1));

        recorder.recordSignal("agent-1", "tenant-1", profile, demand, WorkerOutcome.Success.class);

        verify(signalStore).recordActivation("agent-1", "tenant-1", "Ne");
    }

    @Test
    void failure_recordsCompensatoryFunction() {
        // Agent: dominant=Ti(0.5), auxiliary=Ne(0.3)
        // Demand: Se=0.5, Ti=0.3, Ne=0.2
        // Highest demand NOT in {Ti, Ne} → Se
        var profile = List.of(
                new DispositionValue("Ti", 0.5),
                new DispositionValue("Ne", 0.3));
        var demand = new CognitiveDemand(Map.of("Se", 0.5, "Ti", 0.3, "Ne", 0.2));

        recorder.recordSignal("agent-1", "tenant-1", profile, demand, WorkerOutcome.Declined.class);

        verify(signalStore).recordActivation("agent-1", "tenant-1", "Se");
    }

    @Test
    void failure_allDemandOnDomAux_skipsRecording() {
        var profile = List.of(
                new DispositionValue("Ti", 0.5),
                new DispositionValue("Ne", 0.3));
        var demand = new CognitiveDemand(Map.of("Ti", 0.6, "Ne", 0.4));

        recorder.recordSignal("agent-1", "tenant-1", profile, demand, WorkerOutcome.Declined.class);

        verifyNoInteractions(signalStore);
    }

    @Test
    void noProfile_skipsRecording() {
        recorder.recordSignal("agent-1", "tenant-1", List.of(), null, WorkerOutcome.Success.class);
        verifyNoInteractions(signalStore);
    }

    @Test
    void noDemand_skipsRecording() {
        var profile = List.of(new DispositionValue("Ti", 0.5));
        recorder.recordSignal("agent-1", "tenant-1", profile, null, WorkerOutcome.Success.class);
        verifyNoInteractions(signalStore);
    }
}
```

- [ ] **Step 2: Implement PersonalitySignalRecorder**

```java
package io.casehub.engine.internal.routing;

import io.casehub.api.model.CognitiveDemand;
import io.casehub.eidos.api.DispositionSignalStore;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.worker.api.WorkerOutcome;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PersonalitySignalRecorder {

    private static final Logger LOG = Logger.getLogger(PersonalitySignalRecorder.class);
    private final DispositionSignalStore signalStore;

    @Inject
    public PersonalitySignalRecorder(DispositionSignalStore signalStore) {
        this.signalStore = signalStore;
    }

    public void recordSignal(
            String agentId,
            String tenancyId,
            List<DispositionValue> dispositionProfile,
            CognitiveDemand demand,
            Class<? extends WorkerOutcome> outcomeType) {

        if (dispositionProfile == null || dispositionProfile.isEmpty() || demand == null) {
            return;
        }

        if (WorkerOutcome.Success.class.isAssignableFrom(outcomeType)) {
            recordReinforcement(agentId, tenancyId, dispositionProfile, demand);
        } else {
            recordCompensation(agentId, tenancyId, dispositionProfile, demand);
        }
    }

    private void recordReinforcement(
            String agentId, String tenancyId,
            List<DispositionValue> profile, CognitiveDemand demand) {
        String dominant = profile.get(0).term();
        String auxiliary = profile.size() > 1 ? profile.get(1).term() : null;

        double domDemand = demand.functionWeights().getOrDefault(dominant, 0.0);
        double auxDemand = auxiliary != null ? demand.functionWeights().getOrDefault(auxiliary, 0.0) : 0.0;

        String engaged = domDemand >= auxDemand ? dominant : auxiliary;
        if (engaged == null) engaged = dominant;

        signalStore.recordActivation(agentId, tenancyId, engaged);
        LOG.debugf("Personality reinforcement: agent=%s function=%s", agentId, engaged);
    }

    private void recordCompensation(
            String agentId, String tenancyId,
            List<DispositionValue> profile, CognitiveDemand demand) {
        Set<String> domAux = new HashSet<>();
        domAux.add(profile.get(0).term());
        if (profile.size() > 1) domAux.add(profile.get(1).term());

        String compensatory = demand.functionWeights().entrySet().stream()
                .filter(e -> !domAux.contains(e.getKey()))
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (compensatory == null) {
            LOG.debugf("No compensatory function for agent=%s — all demand on dom/aux", agentId);
            return;
        }

        signalStore.recordActivation(agentId, tenancyId, compensatory);
        LOG.debugf("Personality compensation: agent=%s function=%s", agentId, compensatory);
    }
}
```

- [ ] **Step 3: Inject recorder into WorkflowExecutionCompletedHandler**

Use `ide_read_file` on `WorkflowExecutionCompletedHandler` to find the right injection point and the outcome handling location. Add `@Inject PersonalitySignalRecorder personalitySignalRecorder` field. After outcome processing (after the `WORKER_EXECUTION_FINISHED` event publish), add:

```java
personalitySignalRecorder.record(caseInstance, workerName, capabilityName, outcome);
```

The `record()` public method on the recorder extracts `agentId`, `tenancyId`, `dispositionProfile`, and `CognitiveDemand` from the CaseInstance and CaseDefinition, then delegates to `recordSignal()`.

- [ ] **Step 4: Run tests, verify, commit**

```bash
git -C /Users/mdproctor/claude/casehub/worktrees/49/engine commit -m "feat(#791): PersonalitySignalRecorder — disposition signal recording on task completion"
```

---

### Task 9: PersonalitySignalProvider — alignment scoring

**Refs:** #794, #795

**Files:**
- Create: `runtime/src/main/java/io/casehub/engine/internal/routing/PersonalitySignalProvider.java`
- Create: `runtime/src/test/java/io/casehub/engine/internal/routing/PersonalitySignalProviderTest.java`

**Interfaces:**
- Consumes: `DispositionHealth.probe(descriptor, probeContext) → DispositionStatus` with `effectiveWeights`
- Consumes: `AgentRoutingContext.cognitiveDemand()` (Task 6)
- Consumes: `AgentCandidate.agentDescriptor()` → `disposition().dispositionProfile()`
- Produces: `PersonalitySignalProvider` (id=`"personality"`, `@ApplicationScoped`)

- [ ] **Step 1: Write tests for cosine similarity and provider behavior**

```java
package io.casehub.engine.internal.routing;

import static org.assertj.core.api.Assertions.*;

import io.casehub.api.model.CognitiveDemand;
import io.casehub.api.spi.routing.*;
import io.casehub.eidos.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PersonalitySignalProviderTest {

    @Test
    void id() {
        var provider = new PersonalitySignalProvider(noOpHealth());
        assertThat(provider.id()).isEqualTo("personality");
    }

    @Test
    void noCognitiveDemand_returnsNull() {
        var provider = new PersonalitySignalProvider(noOpHealth());
        var ctx = testContext(null);
        var result = provider.evaluate(ctx, List.of(candidateWithProfile("a", Map.of("Ti", 0.5))));
        assertThat(result).isNull();
    }

    @Test
    void noDispositionProfile_absentFromMap() {
        var provider = new PersonalitySignalProvider(noOpHealth());
        var demand = new CognitiveDemand(Map.of("Ti", 0.6, "Ne", 0.3, "Si", 0.1));
        var ctx = testContext(demand);
        var result = provider.evaluate(ctx, List.of(candidateWithoutProfile("a")));
        assertThat(result).isNull();
    }

    @Test
    void perfectAlignment_scoresHigh() {
        var health = mockHealth(Map.of("Ti", 0.6, "Ne", 0.3, "Si", 0.1));
        var provider = new PersonalitySignalProvider(health);
        var demand = new CognitiveDemand(Map.of("Ti", 0.6, "Ne", 0.3, "Si", 0.1));
        var ctx = testContext(demand);
        var result = provider.evaluate(ctx, List.of(candidateWithProfile("a", Map.of("Ti", 0.6))));

        assertThat(result).isNotNull();
        var signal = result.candidates().get("a");
        assertThat(signal).isInstanceOf(RoutingSignal.CandidateSignal.Score.class);
        assertThat(((RoutingSignal.CandidateSignal.Score) signal).value()).isCloseTo(1.0, within(0.01));
    }

    @Test
    void orthogonalProfile_scoresLow() {
        // Demand is all Ti, agent profile is all Fe — orthogonal
        var health = mockHealth(Map.of("Fe", 0.8));
        var provider = new PersonalitySignalProvider(health);
        var demand = new CognitiveDemand(Map.of("Ti", 1.0));
        var ctx = testContext(demand);
        var result = provider.evaluate(ctx, List.of(candidateWithProfile("a", Map.of("Fe", 0.8))));

        assertThat(result).isNotNull();
        var score = ((RoutingSignal.CandidateSignal.Score) result.candidates().get("a")).value();
        assertThat(score).isCloseTo(0.0, within(0.01));
    }

    @Test
    void cosineSimilarity_correctValue() {
        // demand = {Ti:0.6, Ne:0.4}, profile = {Ti:0.8, Ne:0.2}
        // dot = 0.6*0.8 + 0.4*0.2 = 0.56
        // |demand| = sqrt(0.36+0.16) = sqrt(0.52)
        // |profile| = sqrt(0.64+0.04) = sqrt(0.68)
        // cosine = 0.56 / (0.7211 * 0.8246) = 0.56 / 0.5946 ≈ 0.9418
        double expected = 0.56 / (Math.sqrt(0.52) * Math.sqrt(0.68));

        double actual = PersonalitySignalProvider.cosineSimilarity(
                Map.of("Ti", 0.6, "Ne", 0.4),
                Map.of("Ti", 0.8, "Ne", 0.2));
        assertThat(actual).isCloseTo(expected, within(0.001));
    }

    // --- helpers ---

    private static DispositionHealth noOpHealth() {
        return (descriptor, ctx) -> new DispositionHealth.DispositionStatus.Aligned(Map.of());
    }

    private static DispositionHealth mockHealth(Map<String, Double> effectiveWeights) {
        return (descriptor, ctx) -> new DispositionHealth.DispositionStatus.Aligned(effectiveWeights);
    }

    private static AgentCandidate candidateWithProfile(String id, Map<String, Double> weights) {
        var values = weights.entrySet().stream()
                .map(e -> new DispositionValue(e.getKey(), e.getValue()))
                .toList();
        var disposition = AgentDisposition.builder().dispositionProfile(values).build();
        var descriptor = AgentDescriptor.builder()
                .agentId(id).name(id).slot("test").tenancyId("t1")
                .disposition(disposition).build();
        return new AgentCandidate(id, Set.of(), 0, AgentHealth.READY, descriptor, null);
    }

    private static AgentCandidate candidateWithoutProfile(String id) {
        return new AgentCandidate(id, Set.of(), 0, AgentHealth.READY, null, null);
    }

    private static AgentRoutingContext testContext(CognitiveDemand demand) {
        return new AgentRoutingContext(UUID.randomUUID(), "cap",
                com.fasterxml.jackson.databind.node.NullNode.getInstance(),
                "t1", List.of(), demand, null);
    }
}
```

- [ ] **Step 2: Implement PersonalitySignalProvider**

```java
package io.casehub.engine.internal.routing;

import io.casehub.api.model.CognitiveDemand;
import io.casehub.api.spi.routing.*;
import io.casehub.eidos.api.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;
import org.jboss.logging.Logger;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
public class PersonalitySignalProvider implements RoutingSignalProvider {

    private static final Logger LOG = Logger.getLogger(PersonalitySignalProvider.class);
    private static final String[] FUNCTIONS = {"Ti", "Te", "Fi", "Fe", "Si", "Se", "Ni", "Ne"};

    private final DispositionHealth dispositionHealth;

    @Inject
    public PersonalitySignalProvider(DispositionHealth dispositionHealth) {
        this.dispositionHealth = dispositionHealth;
    }

    @Override
    public String id() {
        return "personality";
    }

    @Override
    public @Nullable RoutingSignal evaluate(AgentRoutingContext context, List<AgentCandidate> eligible) {
        CognitiveDemand demand = context.cognitiveDemand();
        if (demand == null) return null;

        var signals = new LinkedHashMap<String, RoutingSignal.CandidateSignal>();
        for (var candidate : eligible) {
            if (candidate.agentDescriptor() == null) continue;
            var profile = candidate.agentDescriptor().disposition();
            if (profile == null || profile.dispositionProfile().isEmpty()) continue;

            var status = dispositionHealth.probe(candidate.agentDescriptor(),
                    new CapabilityHealth.ProbeContext(context.caseId(), context.capabilityName()));
            Map<String, Double> effectiveWeights = extractWeights(status);

            double similarity = cosineSimilarity(demand.functionWeights(), effectiveWeights);
            signals.put(candidate.workerId(),
                    new RoutingSignal.CandidateSignal.Score(similarity,
                            "personality alignment %.3f".formatted(similarity)));
        }

        return signals.isEmpty() ? null : new RoutingSignal(signals);
    }

    static double cosineSimilarity(Map<String, Double> a, Map<String, Double> b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (String fn : FUNCTIONS) {
            double va = a.getOrDefault(fn, 0.0);
            double vb = b.getOrDefault(fn, 0.0);
            dot += va * vb;
            normA += va * va;
            normB += vb * vb;
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0.0 ? 0.0 : dot / denom;
    }

    private Map<String, Double> extractWeights(DispositionHealth.DispositionStatus status) {
        return switch (status) {
            case DispositionHealth.DispositionStatus.Aligned a -> a.effectiveWeights();
            case DispositionHealth.DispositionStatus.Drifted d -> d.effectiveWeights();
            case DispositionHealth.DispositionStatus.EvolutionPending e -> e.effectiveWeights();
        };
    }
}
```

- [ ] **Step 3: Run tests, verify, commit**

```bash
git -C /Users/mdproctor/claude/casehub/worktrees/49/engine commit -m "feat(#794,#795): PersonalitySignalProvider — cosine alignment scoring"
```

---

### Task 10: Reflection trigger

**Refs:** #793

**Files:**
- Modify: `runtime/src/main/java/io/casehub/engine/internal/routing/PersonalitySignalRecorder.java` — add reflection check after signal recording
- Modify: `runtime/src/test/java/io/casehub/engine/internal/routing/PersonalitySignalRecorderTest.java` — reflection tests

**Interfaces:**
- Consumes: `DispositionHealth.probe()` → `EvolutionPending`
- Consumes: `DispositionEvolution.evaluate(descriptor, pending)` → `Evolved` | `Dampened`
- Consumes: `AgentDescriptorRegistrar.register(updatedDescriptor)` (eidos SPI)
- Consumes: `DispositionSignalStore.decay(agentId, tenancyId, decayFactor)`

- [ ] **Step 1: Write tests for reflection trigger**

```java
@Test
void evolutionPending_evolved_reregistersDescriptor() {
    // After signal recording, probe returns EvolutionPending
    // DispositionEvolution.evaluate returns Evolved
    // Verify AgentDescriptorRegistrar.register called with new profile
    // Verify DispositionSignalStore NOT called with decay (evolution handles it internally)
}

@Test
void evolutionPending_dampened_callsDecay() {
    // After signal recording, probe returns EvolutionPending
    // DispositionEvolution.evaluate returns Dampened(0.2)
    // Verify DispositionSignalStore.decay(agentId, tenancyId, 0.2)
}

@Test
void aligned_noReflection() {
    // probe returns Aligned → no evolution call
}

@Test
void drifted_noReflection() {
    // probe returns Drifted → no evolution call
}
```

- [ ] **Step 2: Add reflection logic to PersonalitySignalRecorder**

Inject `DispositionHealth`, `DispositionEvolution`, `AgentDescriptorRegistrar`. After `recordActivation()`, call `probe()`. If `EvolutionPending`:

```java
private void checkReflection(String agentId, String tenancyId, AgentDescriptor descriptor) {
    var status = dispositionHealth.probe(descriptor,
            new CapabilityHealth.ProbeContext(null, null));

    if (status instanceof DispositionHealth.DispositionStatus.EvolutionPending pending) {
        var result = dispositionEvolution.evaluate(descriptor, pending);
        switch (result) {
            case DispositionEvolution.EvolutionResult.Evolved evolved -> {
                var updated = rebuildDescriptor(descriptor, evolved.newProfile());
                registrar.register(updated);
                LOG.infof("Personality evolved: agent=%s %s→%s",
                        agentId, evolved.previousTypeLabel(), evolved.newTypeLabel());
            }
            case DispositionEvolution.EvolutionResult.Dampened dampened -> {
                signalStore.decay(agentId, tenancyId, dampened.decayFactor());
                LOG.infof("Personality dampened: agent=%s factor=%.2f",
                        agentId, dampened.decayFactor());
            }
        }
    }
}
```

- [ ] **Step 3: Run tests, verify, commit**

```bash
git -C /Users/mdproctor/claude/casehub/worktrees/49/engine commit -m "feat(#793): reflection trigger — evolution detection and handling on signal recording"
```

- [ ] **Step 4: Run full test suite for final verification**

Run: `mvn install -DskipTests -q -f /Users/mdproctor/claude/casehub/worktrees/49/engine/pom.xml`
Then: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -f /Users/mdproctor/claude/casehub/worktrees/49/engine/pom.xml`
Expected: ALL PASS across all modules
