# io.casehub.work.api.BreachDecision.Chained

**Package:** `io.casehub.work.api`

**Kind:** `record`

Tries `primary`; if it cannot be executed, tries `fallback`.
Build with `.thenOnBreach` rather than constructing directly.

## Fields

### `fallback` (`io.casehub.work.api.BreachDecision`)

### `primary` (`io.casehub.work.api.BreachDecision`)

## Record Components

### `fallback` (`io.casehub.work.api.BreachDecision`)

### `primary` (`io.casehub.work.api.BreachDecision`)

## Constructors

### `public Chained(io.casehub.work.api.BreachDecision primary, io.casehub.work.api.BreachDecision fallback)`

#### Parameters

- `primary` (`io.casehub.work.api.BreachDecision`)
- `fallback` (`io.casehub.work.api.BreachDecision`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public io.casehub.work.api.BreachDecision fallback()`

### `public final int hashCode()`

### `public io.casehub.work.api.BreachDecision primary()`

### `public final java.lang.String toString()`
