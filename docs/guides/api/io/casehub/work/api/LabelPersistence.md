# io.casehub.work.api.LabelPersistence

**Package:** `io.casehub.work.api`

**Kind:** `enum`

Determines how a label was applied to a WorkItem and how it is maintained.

<p>
`MANUAL` labels are applied by humans and persist until explicitly removed.
`INFERRED` labels are applied by the filter engine and are recomputed on every
WorkItem mutation — they exist only while the filter condition remains true.

## Enum Constants

### `INFERRED` (`io.casehub.work.api.LabelPersistence`)

Filter-applied. Stripped and recomputed on every WorkItem mutation.
Exists only while at least one FilterChain supports it.

### `MANUAL` (`io.casehub.work.api.LabelPersistence`)

Human-applied. Only removed by an explicit API call or human action.
Never touched by the filter re-evaluation cycle.

## Constructors

### `private LabelPersistence()`

## Methods

### `public static io.casehub.work.api.LabelPersistence valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.work.api.LabelPersistence[] values()`
