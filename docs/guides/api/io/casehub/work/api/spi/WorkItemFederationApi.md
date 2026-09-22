# io.casehub.work.api.spi.WorkItemFederationApi

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

## Methods

### `public abstract void deregister(java.util.UUID subscriptionId, java.lang.String tenancyId)`

#### Parameters

- `subscriptionId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.FederationSubscriptionResult reactivate(java.util.UUID subscriptionId, java.lang.String tenancyId)`

#### Parameters

- `subscriptionId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract io.casehub.work.api.view.FederationSubscriptionResult register(io.casehub.work.api.view.FederationSubscriptionRequest request, java.lang.String tenancyId)`

#### Parameters

- `request` (`io.casehub.work.api.view.FederationSubscriptionRequest`)
- `tenancyId` (`java.lang.String`)
