package com.yeowool.life.competition;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Derives each day's competition purely from the clock and config — every
 * server computes the same window and activity, so nothing has to be
 * coordinated to start one. The activity rotates by epoch day.
 */
public final class CompetitionSchedule {

    public record Window(LocalDate date, CompetitionActivity activity, ZonedDateTime start, ZonedDateTime end) {

        /** yyyy-MM-dd of the start date — the competition's DB key. */
        public String dateKey() {
            return date.toString();
        }

        public boolean contains(ZonedDateTime time) {
            return !time.isBefore(start) && time.isBefore(end);
        }
    }

    private final ZoneId zone;
    private final int startHour;
    private final int durationMinutes;
    private final List<CompetitionActivity> rotation;

    public CompetitionSchedule(ZoneId zone, int startHour, int durationMinutes, List<CompetitionActivity> rotation) {
        this.zone = zone;
        this.startHour = Math.max(0, Math.min(23, startHour));
        this.durationMinutes = Math.max(1, Math.min(1439, durationMinutes));
        this.rotation = rotation.isEmpty() ? List.of(CompetitionActivity.values()) : List.copyOf(rotation);
    }

    public Window windowFor(LocalDate date) {
        CompetitionActivity activity = rotation.get((int) Math.floorMod(date.toEpochDay(), (long) rotation.size()));
        ZonedDateTime start = date.atTime(startHour, 0).atZone(zone);
        return new Window(date, activity, start, start.plusMinutes(durationMinutes));
    }

    public Optional<Window> activeAt(ZonedDateTime now) {
        LocalDate today = now.withZoneSameInstant(zone).toLocalDate();
        for (LocalDate date : List.of(today, today.minusDays(1))) {
            Window window = windowFor(date);
            if (window.contains(now)) {
                return Optional.of(window);
            }
        }
        return Optional.empty();
    }

    /** The next window that hasn't started yet. */
    public Window nextAfter(ZonedDateTime now) {
        LocalDate today = now.withZoneSameInstant(zone).toLocalDate();
        Window window = windowFor(today);
        return window.start().isAfter(now) ? window : windowFor(today.plusDays(1));
    }
}
