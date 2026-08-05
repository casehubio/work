# io.casehub.work.api.spi.WorkerRegistry

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

Resolves a group name to its member `WorkerCandidate`s.

<p>
Implement as `@ApplicationScoped @Alternative @Priority(1)` to connect
LDAP, Keycloak, CaseHub WorkerRegistry, or any directory service.

<p>
The default implementation (`NoOpWorkerRegistry` in the runtime) returns
an empty list for every group — groups remain claim-first until the application
registers a real implementation.

<p>
CaseHub alignment: corresponds to the `WorkerRegistry` concept in casehub-engine.

## Methods

### `public abstract java.util.List<io.casehub.work.api.WorkerCandidate> resolveGroup(java.lang.String groupName)`

Resolve `groupName` to its member candidates.

<p>
Implementations may pre-populate `WorkerCandidate.capabilities()`;
`activeWorkItemCount` is populated by `WorkItemAssignmentService`
if left at 0.

#### Parameters

- `groupName` (`java.lang.String`) — group name as stored in `WorkItem.candidateGroups`

#### Returns

member candidates; empty list if group is unknown or lookup fails
