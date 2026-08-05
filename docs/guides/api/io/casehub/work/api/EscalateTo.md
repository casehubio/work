# io.casehub.work.api.BreachDecision.EscalateTo

**Package:** `io.casehub.work.api`

**Kind:** `record`

Reassigns the WorkItem to `groups` and resets the relevant deadline.

<p>`deadline` applies only to `BreachType.COMPLETION_EXPIRED` breaches;
for `BreachType.CLAIM_EXPIRED`, the new claim deadline is always computed
via `ClaimSlaPolicy` regardless of this field.

<p>An empty `groups` set is treated as "cannot execute" — the runtime
will fall through to the `Chained` fallback, or throw if not chained.

## Fields

### `deadline` (`java.time.Duration`)

### `groups` (`java.util.Set<java.lang.String>`)

## Record Components

### `deadline` (`java.time.Duration`)

### `groups` (`java.util.Set<java.lang.String>`)

## Constructors

### `public EscalateTo(java.util.Set<java.lang.String> groups, java.time.Duration deadline)`

#### Parameters

- `groups` (`java.util.Set<java.lang.String>`)
- `deadline` (`java.time.Duration`)

## Methods

### `public java.time.Duration deadline()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.Set<java.lang.String> groups()`

### `public final int hashCode()`

### `public static io.casehub.work.api.BreachDecision.EscalateTo to(java.lang.String[] groups)`

Creates an `EscalateTo` with no deadline override.
The runtime uses `config.defaultExpiryHours()` for the new completion window.

#### Parameters

- `groups` (`java.lang.String[]`)

#### Throws

- `IllegalArgumentException` — if `groups` is empty — a policy returning
        an empty EscalateTo from outside a `Chained` wrapper would cause
        a silent transaction rollback in the expiry service.

### `public final java.lang.String toString()`

### `public io.casehub.work.api.BreachDecision.EscalateTo withDeadline(java.time.Duration d)`

Returns a copy of this decision with the given deadline override.

#### Parameters

- `d` (`java.time.Duration`)
