# io.casehub.work.api.NormativeResolution

**Package:** `io.casehub.work.api`

**Kind:** `enum`

Normative resolution of a closed work item — grounded in speech act theory.

<ul>
  <li>`.DONE` — work was fulfilled (COMMAND discharged successfully)</li>
  <li>`.DECLINE` — work was deliberately refused (won't be done)</li>
  <li>`.FAILURE` — work was attempted but could not be completed</li>
</ul>

<p>Maps to `WorkItemStatus`:
DONE → COMPLETED, DECLINE → CANCELLED, FAILURE → REJECTED.

<p>Used by `io.casehub.work.issuetracker.webhook.WebhookEvent` to translate
tracker-specific close vocabulary (GitHub `state_reason`, Jira resolution)
into WorkItem terminal transitions without leaking tracker terms past the provider boundary.

## Enum Constants

### `DECLINE` (`io.casehub.work.api.NormativeResolution`)

Work refused — deliberate decision not to proceed.

### `DONE` (`io.casehub.work.api.NormativeResolution`)

Work fulfilled — the obligation was discharged.

### `FAILURE` (`io.casehub.work.api.NormativeResolution`)

Work attempted but could not be completed.

## Constructors

### `private NormativeResolution()`

## Methods

### `public static io.casehub.work.api.NormativeResolution valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.work.api.NormativeResolution[] values()`
