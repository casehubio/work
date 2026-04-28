# quarkus-work — Session Handover
**Date:** 2026-04-28

## Project Status

73 quarkus-work-reports tests (68 H2 + 5 PostgreSQL via Testcontainers). 605 runtime tests. All passing.
Epic #104 closed. Issues #142–145 closed.

## What Was Done This Session

### Epic #104 — SLA Compliance Reporting — CLOSED

New optional module `quarkus-work-reports` (Jandex library, zero cost when absent):
- `GET /workitems/reports/sla-breaches?from=&to=&category=&priority=`
- `GET /workitems/reports/actors/{actorId}?from=&to=&category=`
- `GET /workitems/reports/throughput?from=&to=&groupBy=day|week|month`
- `GET /workitems/reports/queue-health?category=&priority=`
- HQL `CAST(date_trunc('day', w.createdAt) AS LocalDate)` + Java rollup for throughput
- `@CacheResult` Caffeine 5-min TTL (1s in tests)
- PostgreSQL dialect test: works via dedicated Surefire execution (see CLAUDE.md gotcha)

### Build Discipline Scripts
`scripts/mvn-test`, `scripts/mvn-install`, `scripts/mvn-compile`, `scripts/check-build` — hard timeouts (45–90s), clear exit messages. See **Build Discipline** section in CLAUDE.md.

**Root cause documented:** Bash tool `timeout > 120s` silently backgrounds commands with unreadable output. Never specify timeout in Bash tool calls. Use scripts instead.

### Known Production Bug Discovered
Flyway migrations use H2-permissive SQL (`DOUBLE` instead of `DOUBLE PRECISION`). Fails on PostgreSQL. Not yet fixed — filed mentally, needs a dedicated issue.

## Open / Next

| Priority | What |
|---|---|
| 1 | Fix Flyway migration SQL for PostgreSQL compatibility (bare `DOUBLE` → `DOUBLE PRECISION`) |
| 2 | #106 Multi-instance tasks — design check needed (may be CaseHub concern) |
| 3 | #93 Distributed SSE — Redis pub/sub for WorkItemEventBroadcaster |

## Key References

- Build discipline rules: `CLAUDE.md` § Build Discipline
- PostgreSQL test approach: `CLAUDE.md` § Known Quarkiverse gotchas (PostgresDialectValidationTest entry)
- Reports module spec: `docs/superpowers/specs/2026-04-27-sla-reporting-design.md`
- Blog: `blog/2026-04-28-mdp01-optional-reports-postgres-truth.md`
- Garden entries: GE-20260428-336f35, -0482d3, -5dbd37, -e75d4d, -fb8c51, -73d821 (Quarkus augmentation/db-kind)
