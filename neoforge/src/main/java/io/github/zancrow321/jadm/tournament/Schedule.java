package io.github.zancrow321.jadm.tournament;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** The {@code schedule} setting: "20:00" opens a tournament every day, "SAT 18:30" every Saturday. */
final class Schedule {
    private Schedule() {
    }

    static boolean valid(String entry) {
        return parse(entry) != null;
    }

    /** Whether {@code entry} names the minute {@code now} is in. */
    static boolean due(String entry, LocalDateTime now) {
        Object[] when = parse(entry);
        if (when == null) {
            return false;
        }
        LocalTime time = (LocalTime) when[1];
        return (when[0] == null || when[0] == now.getDayOfWeek())
                && now.getHour() == time.getHour() && now.getMinute() == time.getMinute();
    }

    /** @return {day of week or null, time}, or {@code null} if it isn't a schedule entry */
    private static Object[] parse(String entry) {
        String[] parts = entry.strip().split("\\s+");
        if (parts.length == 0 || parts.length > 2) {
            return null;
        }
        DayOfWeek day = null;
        if (parts.length == 2) {
            day = day(parts[0]);
            if (day == null) {
                return null;
            }
        }
        try {
            return new Object[]{day, LocalTime.parse(parts[parts.length - 1].length() == 4
                    ? "0" + parts[parts.length - 1] : parts[parts.length - 1])};
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static DayOfWeek day(String text) {
        String t = text.toUpperCase(Locale.ROOT);
        for (DayOfWeek d : DayOfWeek.values()) {
            if (t.length() >= 2 && d.name().startsWith(t)) {
                return d;
            }
        }
        // German short names: MO DI MI DO FR SA SO
        return switch (t) {
            case "DI" -> DayOfWeek.TUESDAY;
            case "MI" -> DayOfWeek.WEDNESDAY;
            case "DO" -> DayOfWeek.THURSDAY;
            case "SO" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }
}
