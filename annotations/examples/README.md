# casehub-work-annotations — Examples

Four example modules demonstrating annotation-driven human-in-the-loop patterns.

| Module | Annotations | What it demonstrates |
|--------|------------|---------------------|
| `approval-gate-annotated` | `@HumanApproval` | Single approval gate with candidate groups, deadlines, priority |
| `quorum-review-annotated` | `@HumanApproval` + `@RequiresQuorum` + `@Escalate` + `@SkillMatch` | Full composition — M-of-N coordination, escalation policy, skill-based routing |
| `escalation-gate-annotated` | `@HumanApproval` + `@Escalate` + meta-annotation | Escalation without quorum, candidate users, claim deadline escalation, meta-annotation composition, multiple methods per bean |
| `queue-integrated-annotated` | `@HumanApproval` with `types` + `labels` | Queue integration — `types` for JEXL label rule matching, `labels` for direct queue routing, multiple methods per bean |

See `CAPABILITY-MATRIX.md` for the full feature-to-example mapping.

## Building

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn compile -pl annotations/examples/approval-gate-annotated,annotations/examples/quorum-review-annotated,annotations/examples/escalation-gate-annotated,annotations/examples/queue-integrated-annotated
```
