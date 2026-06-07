# CaseHub Work — In-Memory Persistence

Thread-safe, ephemeral in-memory stores for CaseHub Work. Data is lost on restart.

## When to use

- **Tests:** add as `<scope>test</scope>` dependency — in-memory stores beat JPA via CDI priority, no datasource config needed
- **Demos / local evaluation:** add at `compile` scope with datasource deactivation (see below)

## CDI Priority

Tier 3 in the platform CDI priority ladder — `@Alternative @Priority(100)`.

| Tier | Backend | Priority | Module |
|------|---------|----------|--------|
| 0 | No-op (@DefaultBean) | — | core/ (RoutingCursorStore only) |
| 1 | JPA (default) | @ApplicationScoped | runtime/ |
| 2 | MongoDB | @Priority(1) | persistence-mongodb/ |
| **3** | **In-memory (this module)** | **@Priority(100)** | **persistence-memory/** |

When both `persistence-mongodb` and `persistence-memory` are on the classpath, in-memory
wins. This is by design — typical use is `persistence-memory` at test scope alongside
a production backend.

## Thread safety

All stores use `ConcurrentHashMap` (or `ConcurrentHashMap` + `CopyOnWriteArrayList` for
audit entries). Weakly consistent iteration provides READ COMMITTED semantics — the same
isolation level as JPA stores on PostgreSQL.

Objects returned from the store are shared references. Concurrent field-level mutations
to the same object without calling `put()` are not guaranteed to be visible across threads.

## Ephemeral deployment

Add to `application.properties`:

```properties
quarkus.datasource.active=false
quarkus.hibernate-orm.active=false
```

These deactivate JPA extensions at build time. In-memory stores handle all operations.

## Known limitations

- **AuditEntryStore category filter** — silently ignored (requires inter-store dependency to resolve)
- **Shared object references** — see Thread Safety above
