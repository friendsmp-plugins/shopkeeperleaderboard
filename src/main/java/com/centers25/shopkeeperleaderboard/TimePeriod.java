package com.centers25.shopkeeperleaderboard;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Optional;

enum TimePeriod {
    ALL_TIME("All time"), WEEKLY("Weekly"), MONTHLY("Monthly");

    final String label;

    TimePeriod(String label) { this.label = label; }

    String key() { return name().toLowerCase(Locale.ROOT); }

    String start(LocalDate date) {
        return switch (this) {
            case ALL_TIME -> "all";
            case WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
            case MONTHLY -> date.withDayOfMonth(1).toString();
        };
    }

    static Optional<TimePeriod> parse(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "alltime", "all_time", "lifetime" -> Optional.of(ALL_TIME);
            case "weekly", "week" -> Optional.of(WEEKLY);
            case "monthly", "month" -> Optional.of(MONTHLY);
            default -> Optional.empty();
        };
    }
}
