## D1: Binding resolution strategy for GoalDecomposer

**Choice:** GoalDecomposer resolves capability → binding names at decomposition time. One binding per capability (first match). Multiple bindings for the same capability log a warning and pick the first — documented as a v1 limitation.

**Alternatives:**
- Scope all bindings per capability with nested sub-compounds (FirstWins) — correct semantics but introduces compound nesting complexity and entry-condition sequencing overhead
- Add `scopedCapabilities: Set<String>` to Compound for capability-level gating — clean separation but requires threading CaseDefinition into evaluateCompletion and modifying PlanningStrategyLoopControl gating; building without a real multi-binding use case risks wrong semantics

**Rationale:** Current consumers (AML, clinical) use one binding per capability in their case definitions. The multi-binding case is theoretical. Fixing the three existing bugs (capability name ≠ binding name, null executor NPE, PlanItem saved with wrong name) requires the resolution step anyway. Adding capability-level scoping (Option B) is the correct long-term path but should wait for a concrete use case to validate the semantics.

**Trade-offs:** Consumers with multiple bindings targeting the same capability cannot use LLM goal decomposition until capability-level scoping is implemented. ImplementationRoutingStrategy is bypassed for decomposed plans (the GoalDecomposer picks the binding, not the routing strategy).

**Exploration:** deep-analysis
**Status:** captured
