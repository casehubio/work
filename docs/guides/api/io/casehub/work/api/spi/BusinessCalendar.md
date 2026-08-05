# io.casehub.work.api.spi.BusinessCalendar

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

SPI for business-hours-aware deadline calculation.

<p>
Real SLAs are defined in business hours — "48 hours" means 48 working hours,
not 48 wall-clock hours. Implementations advance an `Instant` by a given
`Duration`, counting only time that falls within business windows and
skipping weekends, holidays, and out-of-hours periods.

<p>
The default implementation is driven by `casehub.work.business-hours.*`
config. Override by providing a CDI `@ApplicationScoped` bean that
implements this interface.

<p>
Used by `WorkItemService` to resolve `expiresAtBusinessHours` and
`claimDeadlineBusinessHours` fields to absolute `Instant` values
at WorkItem creation time.

## Methods

### `public abstract java.time.Instant addBusinessDuration(java.time.Instant start, java.time.Duration businessDuration, java.time.ZoneId zone)`

Calculate the `Instant` that is `businessDuration` of business
time after `start`, in the given `ZoneId`.

<p>
Non-business hours, weekends, and holidays are skipped entirely — the clock
only ticks during business windows.

<p>
Example: `start` = Friday 16:00 (Europe/London), `businessDuration`
= 2 hours, business window 09:00–17:00 Mon–Fri → result = Monday 11:00.

#### Parameters

- `start` (`java.time.Instant`) — the starting instant (wall clock)
- `businessDuration` (`java.time.Duration`) — the amount of business time to advance
- `zone` (`java.time.ZoneId`) — the timezone in which business hours are defined

#### Returns

the instant when `businessDuration` of business time has elapsed

### `public abstract boolean isBusinessHour(java.time.Instant instant, java.time.ZoneId zone)`

Returns `true` if the given instant falls within a business hour window
(not a weekend, not a holiday, within the configured daily start/end times).

<p>
Used to determine whether a scheduled deadline check should fire immediately
or wait for the next business window.

#### Parameters

- `instant` (`java.time.Instant`) — the instant to test
- `zone` (`java.time.ZoneId`) — the timezone in which business hours are defined

#### Returns

`true` if `instant` is a business hour
