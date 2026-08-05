# io.casehub.work.api.Capability

**Package:** `io.casehub.work.api`

**Kind:** `record`

A validated capability name. Format: lowercase kebab-case only ([a-z][a-z0-9]*(-[a-z0-9]+)*).
Construction fails fast with `MalformedCapabilityException` on format violations.

## Fields

### `KEBAB_CASE` (`java.util.regex.Pattern`)

### `id` (`java.lang.String`)

## Record Components

### `id` (`java.lang.String`)

## Constructors

### `public Capability(java.lang.String id)`

#### Parameters

- `id` (`java.lang.String`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String id()`

### `public static io.casehub.work.api.Capability of(java.lang.String id)`

#### Parameters

- `id` (`java.lang.String`)

### `public final java.lang.String toString()`
