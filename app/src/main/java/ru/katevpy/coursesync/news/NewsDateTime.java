package ru.katevpy.coursesync.news;

import androidx.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class NewsDateTime {

    private static final String[] MONTH_GENITIVE_RU = {
            "",
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"
    };

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private NewsDateTime() {}

    @Nullable
    public static String format(@Nullable String isoTime) {
        if (isoTime == null || isoTime.isEmpty()) {
            return null;
        }
        String s = isoTime.trim();
        ZonedDateTime zdt = tryParse(s);
        if (zdt == null) {
            return null;
        }
        return formatZoned(zdt);
    }

    @Nullable
    private static ZonedDateTime tryParse(String s) {
        try {
            return OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault());
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(s).atZone(ZoneId.systemDefault());
        } catch (DateTimeParseException ignored) {
        }
        try {
            if (s.length() >= 19) {
                LocalDateTime ldt = LocalDateTime.parse(s.substring(0, 19), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return ldt.atZone(ZoneId.systemDefault());
            }
            if (s.length() >= 16 && s.charAt(10) == 'T') {
                LocalDateTime ldt = LocalDateTime.parse(
                        s.substring(0, 16), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
                return ldt.atZone(ZoneId.systemDefault());
            }
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private static String formatZoned(ZonedDateTime zdt) {
        int day = zdt.getDayOfMonth();
        int month = zdt.getMonthValue();
        String time = TIME.format(zdt);
        return day + " " + MONTH_GENITIVE_RU[month] + " " + time;
    }
}
