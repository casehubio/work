# io.casehub.work.api.spi.HolidayCalendar

**Package:** `io.casehub.work.api.spi`

**Kind:** `interface`

SPI for holiday data — an optional sub-SPI consumed by `BusinessCalendar`
implementations to skip public holidays when advancing business time.

<p>
Provide a CDI `@ApplicationScoped` bean implementing this interface to
plug in any holiday source: a static config list, an iCal feed, a database,
or an external API. The default implementation reads from
`casehub.work.business-hours.holidays`.

<p>
An optional iCal-backed implementation activates automatically when
`casehub.work.business-hours.holiday-ical-url` is configured.

## Methods

### `public abstract boolean isHoliday(java.time.LocalDate date, java.time.ZoneId zone)`

Returns `true` if the given date is a public holiday in the given zone.

<p>
Implementations may ignore `zone` if their holiday data is not
zone-specific (e.g. a UK bank holiday list applies to all UK timezones).

#### Parameters

- `date` (`java.time.LocalDate`) — the date to test
- `zone` (`java.time.ZoneId`) — the timezone context (may be used for regional holiday lookup)

#### Returns

`true` if `date` is a holiday
