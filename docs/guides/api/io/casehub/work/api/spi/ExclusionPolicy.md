# io.casehub.work.api.spi.ExclusionPolicy

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

SPI for evaluating whether a user is excluded from acting on a WorkItem.

<p>
The default implementation (`CommaSeparatedExclusionPolicy`) checks whether
`userId` appears in a comma-separated `excludedUsers` string. Custom
implementations can plug in time-window logic, role-based rules, or any other
conflict-of-interest policy.

<p><b>Activation:</b> declare `@Alternative @Priority(1) @ApplicationScoped`
on your implementation — CDI replaces `CommaSeparatedExclusionPolicy` globally.

<p><b>`excludedUsers` format:</b> the SPI owns the encoding — it is opaque to
the platform. The default uses plain comma-separated actor IDs; custom implementations
may encode richer metadata (e.g. `userId:YYYY-MM-DD` for expiring exclusions).
Implementations that replace the default <em>must</em> handle or reject the plain CSV
format already stored in existing WorkItems.

<p><b>Group-level exclusion:</b> handled separately by
`TemplateExpander + GroupMembershipProvider` — group members are resolved to
actor IDs at WorkItem creation time and stored in `excludedUsers`.
`ExclusionPolicy.check()` operates on individual actor IDs at claim/delegate time.

<p><b>Service-tier enforcement:</b> denials are audited via
`BlockedAttemptAuditService` in a `REQUIRES_NEW` transaction — the
`reason` string from the denied `PolicyDecision` flows into the audit
entry detail field.

<p>
Implementations must return `PolicyDecision.ALLOW` (not `null`) when
the user is not excluded. The reason on a denied decision flows directly into
audit entries and exception messages — make it human-readable and specific.

## Methods

### `public abstract io.casehub.work.api.PolicyDecision check(java.lang.String userId, java.lang.String excludedUsers)`

Evaluates whether `userId` is excluded.

#### Parameters

- `userId` (`java.lang.String`) — the identity to check; must not be null
- `excludedUsers` (`java.lang.String`) — the policy data (e.g. comma-separated IDs); null or blank means no exclusion

#### Returns

`PolicyDecision.ALLOW` if permitted; a denied `PolicyDecision` carrying
        a human-readable reason if excluded
