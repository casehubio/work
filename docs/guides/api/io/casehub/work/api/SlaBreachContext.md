# io.casehub.work.api.SlaBreachContext

**Package:** `io.casehub.work.api`

**Kind:** `record`

Context passed to `SlaBreachPolicy.onBreach` describing the breach event.

<p>`scope` is `Path.root()` when the WorkItem has no assigned scope —
in this case `preferences` reflects org-level defaults only. If the policy
implementation knows a richer scope (e.g. by parsing `task().callerRef()`),
it may resolve preferences independently and ignore this field. See engine#330.

<p>In unit tests, construct with `Path.root()` and `MapPreferences.empty()`
— both are zero-dependency and require no mocking.

## Fields

### `breachType` (`io.casehub.work.api.BreachType`)

### `preferences` (`Preferences`)

### `scope` (`Path`)

### `task` (`io.casehub.work.api.BreachedTask`)

## Record Components

### `breachType` (`io.casehub.work.api.BreachType`)

### `preferences` (`Preferences`)

### `scope` (`Path`)

### `task` (`io.casehub.work.api.BreachedTask`)

## Constructors

### `public SlaBreachContext(io.casehub.work.api.BreachType breachType, io.casehub.work.api.BreachedTask task, Path scope, Preferences preferences)`

#### Parameters

- `breachType` (`io.casehub.work.api.BreachType`)
- `task` (`io.casehub.work.api.BreachedTask`)
- `scope` (`Path`)
- `preferences` (`Preferences`)

## Methods

### `public io.casehub.work.api.BreachType breachType()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public Preferences preferences()`

### `public Path scope()`

### `public io.casehub.work.api.BreachedTask task()`

### `public final java.lang.String toString()`
