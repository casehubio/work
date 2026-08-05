# io.casehub.work.api.spi.InstanceAssignmentStrategy

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

Pluggable multi-instance assignment SPI.

<p>Implementations are CDI beans annotated `@ApplicationScoped` that return
a unique `.id()` string. The strategy is selected per-template via the
`assignmentStrategy` field on `WorkItemTemplate`.

<p>Built-in implementations:
<ul>
<li>`PoolAssignmentStrategy` (id: "pool") — copies parent candidates to all children (default)
<li>`RoundRobinAssignmentStrategy` (id: "round-robin") — distributes across workers
<li>`ExplicitListAssignmentStrategy` (id: "explicit") — assigns from explicit list
<li>`CompositeInstanceAssignmentStrategy` (id: "composite") — chains multiple strategies
</ul>

## Methods

### `public abstract void assign(java.util.List<java.lang.Object> instances, io.casehub.work.api.MultiInstanceContext context)`

Assign candidate users/groups to each instance.
Implementations mutate instance fields (candidateGroups, candidateUsers, assigneeId).

#### Parameters

- `instances` (`java.util.List<java.lang.Object>`) — ordered list of child WorkItems, not yet persisted by this call
- `context` (`io.casehub.work.api.MultiInstanceContext`) — parent WorkItem and resolved MultiInstanceConfig
