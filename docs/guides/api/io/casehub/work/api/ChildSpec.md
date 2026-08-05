# io.casehub.work.api.ChildSpec

**Package:** `io.casehub.work.api`

**Kind:** `record`

Specification for a single child to spawn.

## Fields

### `callerRef` (`java.lang.String`)

### `overrides` (`java.util.Map<java.lang.String,java.lang.Object>`)

### `templateId` (`java.util.UUID`)

## Record Components

### `callerRef` (`java.lang.String`)

opaque routing key stored on the spawned WorkItem and echoed
       in every lifecycle event — never interpreted by quarkus-work;
       CaseHub embeds its planItemId here for completion routing

### `overrides` (`java.util.Map<java.lang.String,java.lang.Object>`)

fields that override the template defaults for this child only
       (e.g. `"candidateGroups"` → `"fraud-team"`)

### `templateId` (`java.util.UUID`)

the UUID of the WorkItemTemplate to instantiate

## Constructors

### `public ChildSpec(java.util.UUID templateId, java.lang.String callerRef, java.util.Map<java.lang.String,java.lang.Object> overrides)`

#### Parameters

- `templateId` (`java.util.UUID`)
- `callerRef` (`java.lang.String`)
- `overrides` (`java.util.Map<java.lang.String,java.lang.Object>`)

## Methods

### `public java.lang.String callerRef()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.Map<java.lang.String,java.lang.Object> overrides()`

### `public java.util.UUID templateId()`

### `public final java.lang.String toString()`
