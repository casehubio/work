# io.casehub.work.api.OnThresholdReached

**Package:** `io.casehub.work.api`

**Kind:** `enum`

Action to take on remaining non-terminal children when the M-of-N completion
threshold is reached in a multi-instance group.

<h2>Choosing a value</h2>
<ul>
  <li><strong>`.KEEP`</strong> — the default and the safe choice. No side effects.
      Children that have not yet completed are left active. Use this when every child's
      outcome matters for audit, reporting, or business reasons even after the threshold
      is met (e.g. regulatory checks where all results must be recorded).</li>
  <li><strong>`.SUSPEND`</strong> — pause active children (ASSIGNED or IN_PROGRESS).
      Signals that their work is on hold while the group outcome is processed. PENDING
      children are left unclaimed. Use this when active work should not be abandoned
      but also should not continue until the group-level decision is reviewed.</li>
  <li><strong>`.CANCEL`</strong> — opt-in only. Cancels all remaining non-terminal
      children immediately. Use this when surplus work is definitively unwanted once the
      threshold is met (e.g. first-N-responders where late completions have no value).
      Callers must set this explicitly — it is never applied by default.</li>
</ul>

<p>
When `onThresholdReached` is not set on a template, the group behaves as
`.KEEP` — no children are modified after the threshold fires.

## Enum Constants

### `CANCEL` (`io.casehub.work.api.OnThresholdReached`)

Cancel all remaining non-terminal children when the threshold is reached.

<p>
<strong>Opt-in only</strong> — must be set explicitly. Never applied by default.
Use when surplus work has no value after the threshold fires, such as first-N-responder
patterns where late completions are irrelevant.

### `KEEP` (`io.casehub.work.api.OnThresholdReached`)

Leave remaining children active to complete naturally.

<p>
This is the default behaviour when `onThresholdReached` is not explicitly set.
No side effects on non-terminal children — every child's outcome is independently
recorded regardless of whether the group threshold has already been met.

### `SUSPEND` (`io.casehub.work.api.OnThresholdReached`)

Suspend active children (ASSIGNED or IN_PROGRESS) when the threshold is reached.

<p>
Signals that in-progress work is on hold while the group-level outcome is processed.
PENDING children (not yet claimed) are left unchanged — suspending unclaimed work
is not meaningful.

## Constructors

### `private OnThresholdReached()`

## Methods

### `public static io.casehub.work.api.OnThresholdReached valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.work.api.OnThresholdReached[] values()`
