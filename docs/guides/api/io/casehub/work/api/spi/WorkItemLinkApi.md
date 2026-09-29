# io.casehub.work.api.spi.WorkItemLinkApi

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

## Methods

### `public abstract io.casehub.work.api.view.WorkItemLinkView addLink(java.util.UUID workItemId, io.casehub.work.api.view.AddLinkRequest body, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `body` (`io.casehub.work.api.view.AddLinkRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract void deleteLink(java.util.UUID workItemId, java.util.UUID linkId, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `linkId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.work.api.view.WorkItemLinkView> listLinks(java.util.UUID workItemId, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)
