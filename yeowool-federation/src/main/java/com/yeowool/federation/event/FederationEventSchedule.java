package com.yeowool.federation.event;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/** Weekly 연합대항 auto-start window (server time zone). Pure — unit-tested. */
public record FederationEventSchedule(DayOfWeek dayOfWeek, LocalTime startTime, int durationMinutes) {

    /** {@code weekKey} is unique per scheduled week, so 3 servers racing to auto-start create one event. */
    public record Window(String weekKey, long startMillis, long endMillis) {
    }

    /** The scheduled window that contains {@code now}, if any. */
    public Optional<Window> activeWindow(ZonedDateTime now) {
        LocalDate startDate = now.toLocalDate().with(TemporalAdjusters.previousOrSame(dayOfWeek));
        ZonedDateTime start = startDate.atTime(startTime).atZone(now.getZone());
        if (now.isBefore(start)) {
            start = start.minusWeeks(1);
        }
        ZonedDateTime end = start.plusMinutes(durationMinutes);
        if (now.isBefore(start) || !now.isBefore(end)) {
            return Optional.empty();
        }
        String weekKey = String.format("%d-W%02d",
                start.get(IsoFields.WEEK_BASED_YEAR), start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        return Optional.of(new Window(weekKey, start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli()));
    }
}
