# Goal Decomposition Binding Bridge

**Issue:** engine#110
**Depends on:** engine#802 (hierarchical planning — landed)

## Problem

`DefaultGoalDecomposer` creates decomposed plans from LLM-generated `GoalStep`
sequences, but the bridge from `GoalStep.capabilityName()` to engine `Binding`
dispatch has three bugs that prevent decomposed plans from executing:

1. **Capability name used as binding name.** `compoundBuilder.binding(step.capabilityName())`
   puts capability names (e.g., `"data-gathering"`) into `scopedBindings`, but
   `PlanningStrategyLoopControl` gates on `Binding.getName()` (e.g., `"gather"`).
   The compound gating is completely bypassed — bindings fire as if unscoped.

2. **Primitive created with null executor.** `new PlanItemDefinition.Primitive(id,
   desc, null, null)` throws `NullPointerException` — the compact constructor
   has `requireNonNull(executor)`.

3. **PlanItems saved with capability name, not binding name.**
   `PlanItemSaveRequest.primitive(..., step.capabilityName(), ...)` means
   `CasePlanModel.findPlanItemByBindingName()` can never find these PlanItems.

The root cause: there is no mapping from capabilities (what the LLM knows) to
bindings (what the engine dispatches). `CaseDefinition` has no reverse lookup
from capability name to binding.

## Design

### Capability → Binding Resolution

Add a resolution utility to `CaseDefinition`:

```java
public List<Binding> findBindingsByCapability(String capabilityName) {
    return getBindings().stream()
        .filter(b -> b.target() instanceof CapabilityTarget ct
            && ct.capability().name().equals(capabilityName))
        .toList();
}
```

`DefaultGoalDecomposer` calls this to resolve each `GoalStep.capabilityName()`
to a concrete binding. The binding's name, executor, and target are then used
throughout the compound creation and PlanItem save path.

### Resolution Semantics (v1)

When `findBindingsByCapability()` returns:

- **Exactly one binding** — use it. This is the expected case.
- **Multiple bindings** — use the first in `CaseDefinition.getBindings()`
  declaration order. Log a warning naming the skipped bindings. This is a v1
  limitation (see Scope Boundaries).
- **No bindings** — skip the step (same as the existing unknown-capability
  filter, but checked against bindings, not capabilities).

Declaration order is deterministic and author-controlled — it does not depend
on LLM output ordering.

### GoalDecomposer Changes

`DefaultGoalDecomposer.decomposeGoal()` currently iterates `GoalStep` nodes and
uses `step.capabilityName()` directly. The fix resolves each step to a binding
first:

```
For each GoalStep:
  bindings = definition.findBindingsByCapability(step.capabilityName())
  if empty → skip (log warning)
  binding = bindings.get(0)  // first in declaration order
  if bindings.size() > 1 → log warning

  // Use binding.getName() everywhere that previously used step.capabilityName()
  compoundBuilder.binding(binding.getName())            // was: step.capabilityName()
  PlanItemSaveRequest.primitive(..., binding.getName())  // was: step.capabilityName()

  // Resolve executor from binding target
  ExecutorRef executor = resolveExecutor(binding, definition)
  compoundBuilder.child(new Primitive(id, desc, executor, null))  // was: null executor
```

### Executor Resolution

The GoalDecomposer needs to resolve `ExecutorRef` from a binding. This is the
same logic already in `PlanningStrategyLoopControl.resolveExecutor()`. Extract
a shared static utility:

```java
public final class BindingExecutorResolver {
    public static ExecutorRef resolve(Binding binding, CaseDefinition definition) {
        return switch (binding.target()) {
            case CapabilityTarget ct -> {
                var matching = definition.getWorkers().stream()
                    .filter(w -> w.capabilityNames().contains(ct.capability().name()))
                    .toList();
                yield matching.isEmpty()
                    ? ExecutorRef.of(ct.capability().name())
                    : ExecutorRef.fromWorker(matching.get(0));
            }
            default -> ExecutorRef.of("unknown");
        };
    }
}
```

`PlanningStrategyLoopControl.resolveExecutor()` delegates to this utility.
`DefaultGoalDecomposer` uses it directly. `ForwardReplanRevision` (plan
adaptation, engine#803) can use it for coherent resolution when materializing
adapted steps.

### Module Placement

| Type | Module | Rationale |
|------|--------|-----------|
| `CaseDefinition.findBindingsByCapability()` | engine-api | Query method on a consumer-visible type |
| `BindingExecutorResolver` | engine-common | Shared between runtime (PlanningStrategyLoopControl) and planning (GoalDecomposer) |
| GoalDecomposer changes | planning | Existing module |

## Testing

### Unit tests

1. **`CaseDefinitionBindingLookupTest`** — `findBindingsByCapability()`:
   - Single binding → returns it
   - Multiple bindings → returns all in declaration order
   - No binding for capability → empty list
   - Non-CapabilityTarget bindings (SubCase, HumanTask) → excluded

2. **`BindingExecutorResolverTest`** — shared executor resolution:
   - CapabilityTarget with matching worker → `ExecutorRef.fromWorker()`
   - CapabilityTarget with no worker → `ExecutorRef.of(capName)`
   - Non-CapabilityTarget → `ExecutorRef.of("unknown")`

3. **`DefaultGoalDecomposerTest` additions** — binding bridge:
   - GoalStep with valid capability → PlanItem uses binding name
   - GoalStep with capability having no binding → step skipped
   - GoalStep with multiple bindings → first selected, warning logged
   - Compound scopedBindings contain binding names, not capability names
   - PlanItem executor is non-null (resolved from binding)
   - Existing tests (idempotency, abandoned goals, linear chain validation)
     continue to pass with binding name resolution

### Integration test

4. **`GoalDecompositionBindingBridgeIntegrationTest`** (`@QuarkusTest`):
   - Full flow: case with goals + LLM strategy → start → GoalSteps resolve to
     bindings → PlanItems created with binding names → compound gates correctly →
     CHOREOGRAPHED dispatch sequences steps → workers execute in order → compound
     completes
   - Mock `ChatModelProvider` with canned JSON returning capability names
   - Uses `casehub-persistence-memory`

## Scope Boundaries

**In scope:**
- `CaseDefinition.findBindingsByCapability()` reverse lookup
- `BindingExecutorResolver` shared utility (extracted from `PlanningStrategyLoopControl`)
- `DefaultGoalDecomposer` binding bridge fixes (all three bugs)
- Tests for the bridge

**v1 limitations (deliberate):**
- One binding per capability in decomposed plans. When multiple bindings target
  the same capability, the first in declaration order is selected. This is
  consistent with the existing v1 limitation "plans must be linear chains
  (sequential only)." The proper fix is capability-level scoping on Compound
  (`scopedCapabilities: Set<String>`) with "any-binding-terminal" completion
  semantics — deferred until a concrete multi-binding use case materializes.
- Case definitions using LLM decomposition should not have mutually-exclusive
  trigger conditions across bindings targeting the same capability (e.g.,
  `.priority == "fast"` vs `.priority == "deep"`). If the GoalDecomposer
  selects the wrong binding, the step may deadlock. The proper fix
  (capability-level scoping) allows all bindings to remain eligible and their
  triggers to resolve at dispatch time.
- `ImplementationRoutingStrategy` is structurally unreachable for decomposed
  plans — the compound scopes exactly one binding per capability, so the
  "group size > 1" branch in `applyImplementationRouting()` never fires.
  The default strategy is `NoOp` (returns `RunAll`), so no behavior change
  in current deployments.
- Plan adaptation (`ForwardReplanRevision`) should use `BindingExecutorResolver`
  for coherent resolution. Wiring this is recommended but not gated by this
  issue — adaptation operates on compounds, not on the initial decomposition
  path.

**Out of scope:**
- Capability-level scoping on Compound (`scopedCapabilities: Set<String>`)
- Multi-binding completion semantics ("any-binding-terminal")
- Changes to `PlanningStrategyLoopControl` gating logic
- Changes to `CompoundCompletionEvaluator`
- Worker-centric vs case-centric goal iteration (existing architecture from #802)
- Re-decomposition on context change (#803)
