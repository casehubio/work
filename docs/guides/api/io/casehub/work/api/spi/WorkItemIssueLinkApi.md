# io.casehub.work.api.spi.WorkItemIssueLinkApi

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

## Methods

### `public abstract io.casehub.work.api.view.IssueLinkView createAndLink(java.util.UUID workItemId, io.casehub.work.api.view.CreateIssueRequest request, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `request` (`io.casehub.work.api.view.CreateIssueRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.IssueLinkView linkIssue(java.util.UUID workItemId, io.casehub.work.api.view.LinkIssueRequest request, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `request` (`io.casehub.work.api.view.LinkIssueRequest`)
- `tenancyId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.work.api.view.IssueLinkView> listLinks(java.util.UUID workItemId, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract void removeLink(java.util.UUID workItemId, java.util.UUID linkId, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `linkId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.SyncLinksResult syncLinks(java.util.UUID workItemId, java.lang.String tenancyId)`

#### Parameters

- `workItemId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)
