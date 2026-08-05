# io.casehub.work.api.Outcome

**Package:** `io.casehub.work.api`

**Kind:** `record`

A named completion classification declared on a WorkItemTemplate.

<p>
Outcomes give templates a machine-readable set of valid result states
(e.g. `approved`, `rejected`, `needs-revision`). The engine
can switch on `outcome` in `outputMapping` without parsing the
free-form `resolution` field. Aligned with the Open Human Task (OHT) spec.

## Fields

### `condition` (`java.lang.String`)

### `displayName` (`java.lang.String`)

### `name` (`java.lang.String`)

## Record Components

### `condition` (`java.lang.String`)

Optional JEXL expression evaluated at completion/rejection time.
                    Null means unconditional — the outcome is always applicable.
                    When non-null, the outcome is rejected with 400 if the expression
                    evaluates to false. Expression may reference `workItem.*`,
                    `resolution`, `reason`, and `actorId`.

### `displayName` (`java.lang.String`)

Human-readable label resolved via template lookup; null when not set.

### `name` (`java.lang.String`)

Machine-readable key — lowercase, URL-safe (e.g. `needs-revision`).

## Constructors

### `public Outcome(java.lang.String name, java.lang.String displayName, java.lang.String condition)`

#### Parameters

- `name` (`java.lang.String`)
- `displayName` (`java.lang.String`)
- `condition` (`java.lang.String`)

## Methods

### `public java.lang.String condition()`

### `public java.lang.String displayName()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String name()`

### `public final java.lang.String toString()`
