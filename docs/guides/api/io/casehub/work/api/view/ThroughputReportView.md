# io.casehub.work.api.view.ThroughputReportView

**Package:** `io.casehub.work.api.view`

**Kind:** `record`

## Fields

### `buckets` (`java.util.List<io.casehub.work.api.view.ThroughputBucketView>`)

### `from` (`java.time.Instant`)

### `groupBy` (`java.lang.String`)

### `to` (`java.time.Instant`)

## Record Components

### `buckets` (`java.util.List<io.casehub.work.api.view.ThroughputBucketView>`)

### `from` (`java.time.Instant`)

### `groupBy` (`java.lang.String`)

### `to` (`java.time.Instant`)

## Constructors

### `public ThroughputReportView(java.time.Instant from, java.time.Instant to, java.lang.String groupBy, java.util.List<io.casehub.work.api.view.ThroughputBucketView> buckets)`

#### Parameters

- `from` (`java.time.Instant`)
- `to` (`java.time.Instant`)
- `groupBy` (`java.lang.String`)
- `buckets` (`java.util.List<io.casehub.work.api.view.ThroughputBucketView>`)

## Methods

### `public java.util.List<io.casehub.work.api.view.ThroughputBucketView> buckets()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.time.Instant from()`

### `public java.lang.String groupBy()`

### `public final int hashCode()`

### `public java.time.Instant to()`

### `public final java.lang.String toString()`
