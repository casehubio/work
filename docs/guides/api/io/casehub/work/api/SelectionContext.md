# io.casehub.work.api.SelectionContext

**Package:** `io.casehub.work.api`

**Kind:** `record`

Minimal WorkItem context passed to `WorkerSelectionStrategy.select`.

<p>Decouples strategies from the WorkItem JPA entity.

## Fields

### `candidateGroups` (`java.lang.String`)

### `candidateUsers` (`java.lang.String`)

### `description` (`java.lang.String`)

### `excludedUsers` (`java.lang.String`)

### `minimumScore` (`java.lang.Double`)

### `priority` (`java.lang.String`)

### `requiredCapabilities` (`java.util.Set<io.casehub.work.api.Capability>`)

### `routingStrategy` (`java.lang.String`)

### `title` (`java.lang.String`)

### `types` (`java.util.List<java.lang.String>`)

## Record Components

### `candidateGroups` (`java.lang.String`)

comma-separated group names (may be null)

### `candidateUsers` (`java.lang.String`)

comma-separated user IDs (may be null)

### `description` (`java.lang.String`)

work item description — used by semantic matchers (may be null)

### `excludedUsers` (`java.lang.String`)

comma-separated user IDs excluded from this WorkItem (may be null)

### `minimumScore` (`java.lang.Double`)

per-WorkItem score floor for scoring strategies (null = no floor)

### `priority` (`java.lang.String`)

WorkItemPriority name e.g. "HIGH" (may be null)

### `requiredCapabilities` (`java.util.Set<io.casehub.work.api.Capability>`)

capabilities the assignee must possess (empty set = no requirement);
    matched against worker capability tags using exact case-sensitive equality

### `routingStrategy` (`java.lang.String`)

per-WorkItem `WorkerSelectionStrategy` id override (null = use global config)

### `title` (`java.lang.String`)

work item title — used by semantic matchers (may be null)

### `types` (`java.util.List<java.lang.String>`)

WorkItem type paths (may be null or empty)

## Constructors

### `public SelectionContext(java.util.List<java.lang.String> types, java.lang.String priority, java.util.Set<io.casehub.work.api.Capability> requiredCapabilities, java.lang.String candidateGroups, java.lang.String candidateUsers, java.lang.String title, java.lang.String description, java.lang.String excludedUsers, java.lang.String routingStrategy, java.lang.Double minimumScore)`

#### Parameters

- `types` (`java.util.List<java.lang.String>`)
- `priority` (`java.lang.String`)
- `requiredCapabilities` (`java.util.Set<io.casehub.work.api.Capability>`)
- `candidateGroups` (`java.lang.String`)
- `candidateUsers` (`java.lang.String`)
- `title` (`java.lang.String`)
- `description` (`java.lang.String`)
- `excludedUsers` (`java.lang.String`)
- `routingStrategy` (`java.lang.String`)
- `minimumScore` (`java.lang.Double`)

## Methods

### `public java.lang.String candidateGroups()`

### `public java.lang.String candidateUsers()`

### `public java.lang.String description()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.String excludedUsers()`

### `public final int hashCode()`

### `public java.lang.Double minimumScore()`

### `public java.lang.String priority()`

### `public java.util.Set<io.casehub.work.api.Capability> requiredCapabilities()`

### `public java.lang.String routingStrategy()`

### `public java.lang.String title()`

### `public final java.lang.String toString()`

### `public java.util.List<java.lang.String> types()`
