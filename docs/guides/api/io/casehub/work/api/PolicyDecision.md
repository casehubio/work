# io.casehub.work.api.PolicyDecision

**Package:** `io.casehub.work.api`

**Kind:** `record`

Result of an `ExclusionPolicy.check` evaluation.

<p>Carries whether the check denied the operation and, if so, the reason
supplied by the policy implementation. The reason is policy-specific and
intended for audit entries and exception messages — the caller should not
parse it programmatically.

<p>Use `.ALLOW` for the common non-denied case to avoid allocation.

## Fields

### `ALLOW` (`io.casehub.work.api.PolicyDecision`)

Shared instance for the non-denied case — reason is `null`.

### `denied` (`boolean`)

### `reason` (`java.lang.String`)

## Record Components

### `denied` (`boolean`)

### `reason` (`java.lang.String`)

## Constructors

### `public PolicyDecision(boolean denied, java.lang.String reason)`

#### Parameters

- `denied` (`boolean`)
- `reason` (`java.lang.String`)

## Methods

### `public boolean allowed()`

Convenience inverse of `.denied()`.

### `public boolean denied()`

### `public static io.casehub.work.api.PolicyDecision deny(java.lang.String reason)`

Returns a denied decision carrying the supplied reason.

#### Parameters

- `reason` (`java.lang.String`)

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String reason()`

### `public final java.lang.String toString()`
