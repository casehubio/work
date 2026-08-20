# Capability Matrix — casehub-work-annotations

Maps annotation features to example modules and deployment tests.

## @HumanApproval

| Feature | approval-gate | quorum-review | escalation-gate | queue-integrated | Deployment test |
|---------|:---:|:---:|:---:|:---:|:---:|
| `title` (required) | ✅ | ✅ | ✅ | ✅ | ✅ |
| `candidateGroups` | ✅ | ✅ | | ✅ | ✅ |
| `candidateUsers` | | | ✅ | | |
| `priority` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `claimDeadline` | ✅ | | ✅ | | ✅ |
| `expiresAt` | ✅ | | ✅ | | |
| `description` | | | ✅ | | |
| `types` | | | | ✅ | |
| `labels` | | | | ✅ | ✅ |

## @RequiresQuorum

| Feature | approval-gate | quorum-review | escalation-gate | queue-integrated | Deployment test |
|---------|:---:|:---:|:---:|:---:|:---:|
| `instances` + `required` | | ✅ | | | ✅ |
| `candidateGroups` | | | | | |
| `onThresholdReached` | | ✅ | | | |
| `allowSameAssignee` | | | | | |

## @Escalate

| Feature | approval-gate | quorum-review | escalation-gate | queue-integrated | Deployment test |
|---------|:---:|:---:|:---:|:---:|:---:|
| `onExpiry` | | ✅ | ✅ | | ✅ |
| `onClaimDeadline` | | | ✅ | | |
| `deadline` | | ✅ | ✅ | | |
| `generateSummary` | | ✅ | ✅ | | |

## @SkillMatch

| Feature | approval-gate | quorum-review | escalation-gate | queue-integrated | Deployment test |
|---------|:---:|:---:|:---:|:---:|:---:|
| `strategy` | | ✅ | | | |
| `requiredCapabilities` | | ✅ | | | |
| `minimumScore` | | ✅ | | | ✅ |

## Composition Patterns

| Pattern | approval-gate | quorum-review | escalation-gate | queue-integrated | Deployment test |
|---------|:---:|:---:|:---:|:---:|:---:|
| `@HumanApproval` alone | ✅ | | | ✅ | ✅ |
| `@HumanApproval` + `@Escalate` | | | ✅ | | |
| `@HumanApproval` + `@RequiresQuorum` + `@Escalate` + `@SkillMatch` | | ✅ | | | |
| Meta-annotation composition | | | ✅ | | |
| Multiple methods per bean | | | ✅ | ✅ | |
| Queue integration (types + labels) | | | | ✅ | |
| `@Escalate` alone (build error) | | | | | ✅ |
| `@SkillMatch` alone (build error) | | | | | ✅ |
| `void` return (build error) | | | | | ✅ |
| Invalid duration (build error) | | | | | ✅ |
| Quorum out of range (build error) | | | | | ✅ |
| `minimumScore` out of range (build error) | | | | | ✅ |
| Label with whitespace (build error) | | | | | ✅ |

## Coverage Summary

- **30 feature cells** across 4 annotations (added `types`, `labels`)
- **23 covered** by examples (77%)
- **7 uncovered** — `@RequiresQuorum.candidateGroups`, `@RequiresQuorum.allowSameAssignee`, `@RequiresQuorum` standalone, `@SkillMatch` standalone
- **7 negative cases** covered by deployment tests (added whitespace label validation)

## Queue Integration Notes

Annotation-created WorkItems become queue-visible through two paths:

1. **Direct labels** (`@HumanApproval(labels = {...})`) — MANUAL labels set at creation. Queue views matching the label pattern see the WorkItem immediately.
2. **Inferred labels** (LabelRuleEngine) — JEXL rules evaluate WorkItem properties (`types`, `priority`, `candidateGroups`) and add INFERRED labels. The `types` attribute on `@HumanApproval` feeds into `types.contains('X')` expressions.

Both paths compose: direct labels provide immediate queue membership, inferred labels add dynamic routing on top. See `queues-examples/` for LabelRule patterns (FinanceApprovalScenario, SupportTriageScenario).
