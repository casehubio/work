# io.casehub.work.api.spi.WorkItemSkillProfileApi

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

## Methods

### `public abstract void delete(java.lang.String workerId, java.lang.String tenancyId)`

#### Parameters

- `workerId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.SkillProfileView get(java.lang.String workerId, java.lang.String tenancyId)`

#### Parameters

- `workerId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.work.api.view.SkillProfileView> listAll(java.lang.String tenancyId)`

#### Parameters

- `tenancyId` (`java.lang.String`)

### `public abstract void upsert(io.casehub.work.api.view.SkillProfileRequest request, java.lang.String tenancyId)`

#### Parameters

- `request` (`io.casehub.work.api.view.SkillProfileRequest`)
- `tenancyId` (`java.lang.String`)
