# io.casehub.work.api.spi.SpawnPort

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

SPI for spawning child work units from a parent.
Implementations live in domain-specific modules (quarkus-work runtime for WorkItems).
quarkus-work fires events and wires PART_OF relations; it makes no decisions
about what child completion means.

## Methods

### `public abstract void cancelGroup(java.util.UUID groupId, boolean cascadeChildren)`

#### Parameters

- `groupId` (`java.util.UUID`)
- `cascadeChildren` (`boolean`)

### `public abstract io.casehub.work.api.SpawnResult spawn(io.casehub.work.api.SpawnRequest request)`

#### Parameters

- `request` (`io.casehub.work.api.SpawnRequest`)
