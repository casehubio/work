# Capability Matrix — casehub-work-annotations

Maps annotation features to example modules and deployment tests.

## @HumanApproval

| Feature | approval-gate | quorum-review | escalation-gate | Deployment test |
|---------|:---:|:---:|:---:|:---:|
| `title` (required) | ✅ | ✅ | ✅ | ✅ |
| `candidateGroups` | ✅ | ✅ | | ✅ |
| `candidateUsers` | | | ✅ | |
| `priority` | ✅ | ✅ | ✅ | ✅ |
| `claimDeadline` | ✅ | | ✅ | ✅ |
| `expiresAt` | ✅ | | ✅ | |
| `description` | | | ✅ | |

## @RequiresQuorum

| Feature | approval-gate | quorum-review | escalation-gate | Deployment test |
|---------|:---:|:---:|:---:|:---:|
| `instances` + `required` | | ✅ | | ✅ |
| `candidateGroups` | | | | |
| `onThresholdReached` | | ✅ | | |
| `allowSameAssignee` | | | | |

## @Escalate

| Feature | approval-gate | quorum-review | escalation-gate | Deployment test |
|---------|:---:|:---:|:---:|:---:|
| `onExpiry` | | ✅ | ✅ | ✅ |
| `onClaimDeadline` | | | ✅ | |
| `deadline` | | ✅ | ✅ | |
| `generateSummary` | | ✅ | ✅ | |

## @SkillMatch

| Feature | approval-gate | quorum-review | escalation-gate | Deployment test |
|---------|:---:|:---:|:---:|:---:|
| `strategy` | | ✅ | | |
| `requiredCapabilities` | | ✅ | | |
| `minimumScore` | | ✅ | | ✅ |

## Composition Patterns

| Pattern | approval-gate | quorum-review | escalation-gate | Deployment test |
|---------|:---:|:---:|:---:|:---:|
| `@HumanApproval` alone | ✅ | | | ✅ |
| `@HumanApproval` + `@Escalate` | | | ✅ | |
| `@HumanApproval` + `@RequiresQuorum` + `@Escalate` + `@SkillMatch` | | ✅ | | |
| Meta-annotation composition | | | | **gap** — needs `@Target(ANNOTATION_TYPE)` on all annotations |
| Multiple methods per bean | | | ✅ | |
| `@Escalate` alone (build error) | | | | ✅ |
| `@SkillMatch` alone (build error) | | | | ✅ |
| `void` return (build error) | | | | ✅ |
| Invalid duration (build error) | | | | ✅ |
| Quorum out of range (build error) | | | | ✅ |
| `minimumScore` out of range (build error) | | | | ✅ |

## Coverage Summary

- **28 feature cells** across 4 annotations
- **19 covered** by examples (68%)
- **9 uncovered** — `@RequiresQuorum.candidateGroups`, `@RequiresQuorum.allowSameAssignee`, `@RequiresQuorum` standalone, `@HumanApproval.candidateUsers` + `@RequiresQuorum` combo, `@SkillMatch` standalone patterns
- **6 negative cases** covered by deployment tests
