# io.casehub.work.api.view.FederationSubscriptionRequest

**Package:** `io.casehub.work.api.view`

**Kind:** `record`

## Fields

### `baseUrl` (`java.lang.String`)

### `callbackUrl` (`java.lang.String`)

### `capabilitiesJson` (`java.lang.String`)

### `filter` (`io.casehub.work.api.view.FederationFilterRequest`)

### `hmacSecret` (`java.lang.String`)

### `peerId` (`java.lang.String`)

### `tenancyId` (`java.lang.String`)

## Record Components

### `baseUrl` (`java.lang.String`)

### `callbackUrl` (`java.lang.String`)

### `capabilitiesJson` (`java.lang.String`)

### `filter` (`io.casehub.work.api.view.FederationFilterRequest`)

### `hmacSecret` (`java.lang.String`)

### `peerId` (`java.lang.String`)

### `tenancyId` (`java.lang.String`)

## Constructors

### `public FederationSubscriptionRequest(java.lang.String peerId, java.lang.String callbackUrl, java.lang.String baseUrl, java.lang.String tenancyId, io.casehub.work.api.view.FederationFilterRequest filter, java.lang.String capabilitiesJson, java.lang.String hmacSecret)`

#### Parameters

- `peerId` (`java.lang.String`)
- `callbackUrl` (`java.lang.String`)
- `baseUrl` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
- `filter` (`io.casehub.work.api.view.FederationFilterRequest`)
- `capabilitiesJson` (`java.lang.String`)
- `hmacSecret` (`java.lang.String`)

## Methods

### `public java.lang.String baseUrl()`

### `public java.lang.String callbackUrl()`

### `public java.lang.String capabilitiesJson()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public io.casehub.work.api.view.FederationFilterRequest filter()`

### `public final int hashCode()`

### `public java.lang.String hmacSecret()`

### `public java.lang.String peerId()`

### `public java.lang.String tenancyId()`

### `public final java.lang.String toString()`
