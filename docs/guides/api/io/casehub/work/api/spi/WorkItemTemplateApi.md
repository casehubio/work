# io.casehub.work.api.spi.WorkItemTemplateApi

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

## Methods

### `public abstract io.casehub.work.api.view.TemplateView create(io.casehub.work.api.view.CreateTemplateRequest request, java.lang.String tenancyId)`

#### Parameters

- `request` (`io.casehub.work.api.view.CreateTemplateRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract void delete(java.util.UUID templateId, java.lang.String tenancyId)`

#### Parameters

- `templateId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.TemplateView getById(java.util.UUID templateId, java.lang.String tenancyId)`

#### Parameters

- `templateId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.WorkItemView instantiate(java.util.UUID templateId, io.casehub.work.api.view.InstantiateTemplateRequest request, java.lang.String tenancyId)`

#### Parameters

- `templateId` (`java.util.UUID`)
- `request` (`io.casehub.work.api.view.InstantiateTemplateRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.work.api.view.TemplateView> listAll(java.lang.String tenancyId)`

#### Parameters

- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.TemplateView update(java.util.UUID templateId, io.casehub.work.api.view.UpdateTemplateRequest request, java.lang.String tenancyId)`

#### Parameters

- `templateId` (`java.util.UUID`)
- `request` (`io.casehub.work.api.view.UpdateTemplateRequest`)
- `tenancyId` (`java.lang.String`)
