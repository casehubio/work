# io.casehub.work.api.CrossSystemRef

**Package:** `io.casehub.work.api`

**Kind:** `interface`

Marker interface for typed cross-system references carried on WorkItem callerRef.

<p>Each integration module provides its own implementations with domain-specific
accessors. work-api stays opaque about callerRef content — this interface provides
only `.system()` for runtime identification and `.encode()` for
string serialisation.

## Methods

### `public abstract java.lang.String encode()`

### `public abstract java.lang.String system()`
