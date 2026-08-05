# io.casehub.work.api.spi.SkillProfileProvider

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

SPI for building a worker's `SkillProfile`.

<p>
Implement as `@ApplicationScoped @Alternative @Priority(1)` to override
the active built-in. Built-in implementations (in quarkus-work-ai):
`CapabilitiesSkillProfileProvider`, `WorkerProfileSkillProfileProvider`,
`ResolutionHistorySkillProfileProvider`.

## Methods

### `public abstract io.casehub.work.api.SkillProfile getProfile(java.lang.String workerId, java.util.Set<java.lang.String> capabilities)`

Build a skill profile for the given worker.

#### Parameters

- `workerId` (`java.lang.String`) — the worker identifier
- `capabilities` (`java.util.Set<java.lang.String>`) — the worker's declared capabilities (from `WorkerCandidate`)

#### Returns

the worker's skill profile; never null
