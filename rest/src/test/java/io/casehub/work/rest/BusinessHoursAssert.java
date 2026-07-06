package io.casehub.work.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

final class BusinessHoursAssert {

    private BusinessHoursAssert() {}

    static void assertDeadlineInRange(final Instant deadline, final Instant before,
            final int businessHours) {
        assertThat(deadline)
                .as("deadline should be after the request was made")
                .isAfter(before);
        assertThat(deadline)
                .as("deadline should be within %d calendar days for %d business hours",
                        maxCalendarDays(businessHours), businessHours)
                .isBefore(maxBound(before, businessHours));
    }

    static Instant maxBound(final Instant before, final int businessHours) {
        return before.plus(maxCalendarDays(businessHours), ChronoUnit.DAYS)
                     .plus(1, ChronoUnit.HOURS);
    }

    static int maxCalendarDays(final int businessHours) {
        return (int) Math.ceil(businessHours / 8.0) + 2;
    }
}
