# io.casehub.work.api.WorkItemRelationType

**Package:** `io.casehub.work.api`

**Kind:** `class`

Well-known relation type constants for `WorkItemRelation`.

<h2>Extensibility — this is not an enum</h2>
<p>
Relation types are plain strings stored in the `relation_type` column.
This class provides named constants for discoverability, but consuming
applications can use <em>any non-blank string</em> as a relation type without
any schema change, registration, or configuration:

<pre>`// Well-known type \u2014 use the constant
"PART_OF"

// Custom type \u2014 just use the string
"TRIGGERED_BY"
"APPROVED_BY"
"ESCALATED_FROM"
"RESOLVED_BY"`</pre>

<h2>Directionality</h2>
<p>
All relations are directed: `source \u2192 target`.
<ul>
<li>`"child" PART_OF "parent"` — child is a member of the parent group</li>
<li>`"A" BLOCKS "B"` — A must be resolved before B can proceed</li>
<li>`"A" RELATES_TO "B"` — bidirectional by convention; store two rows if both
directions are needed</li>
</ul>

<h2>UI implications</h2>
<p>
`.PART_OF` enables:
<ul>
<li>Tree navigation — traverse parent and children via REST</li>
<li>Group summaries — X of Y children completed</li>
<li>Breadcrumb trails — walk PART_OF chain to root</li>
<li>"Top-level only" filter — WorkItems with no outgoing PART_OF</li>
<li>Epic-style views — one root WorkItem with a child list</li>
</ul>

## Fields

### `BLOCKED_BY` (`java.lang.String`)

The source WorkItem cannot proceed until the target is resolved.
Inverse of `.BLOCKS`.

### `BLOCKS` (`java.lang.String`)

The source WorkItem must be resolved before the target can proceed.
Inverse of `.BLOCKED_BY`.

### `DUPLICATES` (`java.lang.String`)

The source WorkItem is a duplicate of the target.
The source is typically closed and the target kept as the canonical item.

### `INVERSES` (`java.util.Map<java.lang.String,java.lang.String>`)

### `PART_OF` (`java.lang.String`)

The source WorkItem is a component of the target WorkItem.
Directed: `child \u2192 parent`.

<p>
Cycle prevention is enforced at the application layer for this type —
a WorkItem cannot be its own ancestor.

### `RELATES_TO` (`java.lang.String`)

The source and target WorkItems are contextually related.
Symmetric by convention — store two rows for a true bidirectional link.

## Constructors

### `private WorkItemRelationType()`

## Methods

### `public static java.lang.String inverse(java.lang.String relationType)`

Return the semantic inverse of the given relation type, if one is defined.

<p>
Only applies to asymmetric pairs (`.BLOCKS` ↔ `.BLOCKED_BY`).
Returns `null` for types with no defined inverse (`.PART_OF`,
`.RELATES_TO`, `.DUPLICATES`, and all custom types).

#### Parameters

- `relationType` (`java.lang.String`) — the relation type to invert

#### Returns

the inverse type, or `null` if none is defined
