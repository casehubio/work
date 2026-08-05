# io.casehub.work.api.spi.CapabilityRegistry

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

SPI for known capability vocabulary.

<p>The default implementation (`io.casehub.work.core.strategy.PermissiveCapabilityRegistry`)
returns an empty set — no enforcement. Deploy an `@ApplicationScoped @Alternative @Priority(1)`
implementation to govern capability vocabulary.

<p>Validation mode (STRICT / WARN / PERMISSIVE) is configured via
`casehub.work.capability-validation` — it is a deployment concern, not a registry concern.

## Methods

### `public abstract java.util.Set<io.casehub.work.api.Capability> capabilities()`

Known capability vocabulary. Empty set means unmanaged (no enforcement).

### `public default boolean isKnown(io.casehub.work.api.Capability tag)`

Returns true if `tag` is a known capability.

<p>Matching is exact and case-sensitive. The `Capability` constructor enforces
lowercase kebab-case, so format violations are rejected before reaching this method.

<p>Override when direct lookup is more efficient than loading `.capabilities()`
(e.g. a database-backed registry with `SELECT EXISTS`). Never back this method
with a static field — that silently bypasses subclass capability sets (GE-20260511-a5f47d).

#### Parameters

- `tag` (`io.casehub.work.api.Capability`)
