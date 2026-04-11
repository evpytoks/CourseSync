package ru.katevpy.coursesync.calendar;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CalendarListItem;

public final class CalendarScheduleTimelineBinder {

    private static final int HOURS = 24;
    private static final int DEFAULT_DURATION_MIN = 60;

    private CalendarScheduleTimelineBinder() {
    }

    public static void clear(@Nullable FrameLayout host) {
        if (host == null) {
            return;
        }
        host.removeAllViews();
    }

    public static void bindDay(
            @NonNull Fragment fragment,
            @NonNull FrameLayout host,
            @NonNull LocalDate day,
            @NonNull List<CalendarListItem> events,
            int navigateActionId) {
        Context ctx = fragment.requireContext();
        host.removeAllViews();
        int defaultColor = ContextCompat.getColor(ctx, R.color.calendar_event_type_default);

        List<CalendarListItem> allDay = new ArrayList<>();
        List<Timed> timed = new ArrayList<>();
        for (CalendarListItem item : events) {
            Parsed p = parse(item);
            if (p == null || !p.date.equals(day)) {
                continue;
            }
            if (p.allDay) {
                allDay.add(item);
            } else {
                timed.add(new Timed(item, p.startMinute, Math.min(p.startMinute + DEFAULT_DURATION_MIN, HOURS * 60)));
            }
        }
        Collections.sort(timed, Comparator.comparingInt(t -> t.startMin));

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout allDayRow = buildAllDayRow(ctx, allDay, defaultColor, fragment, navigateActionId);
        root.addView(allDayRow);

        if (!timed.isEmpty()) {
            LinearLayout stack = new LinearLayout(ctx);
            stack.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams stackLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (!allDay.isEmpty()) {
                stackLp.topMargin = timelineBlockGapPx(ctx);
            }
            stack.setLayoutParams(stackLp);
            addTimedEventStack(
                    ctx,
                    stack,
                    timed,
                    defaultColor,
                    fragment,
                    navigateActionId,
                    LinearLayout.LayoutParams.MATCH_PARENT);
            root.addView(stack);
        }

        host.addView(root);
    }

    public static void bindWeek(
            @NonNull Fragment fragment,
            @NonNull FrameLayout host,
            @NonNull LocalDate weekStart,
            @NonNull List<CalendarListItem> events,
            int navigateActionId,
            @NonNull Consumer<LocalDate> onDayHeaderClick) {
        Context ctx = fragment.requireContext();
        host.removeAllViews();
        int colMinPx = ctx.getResources().getDimensionPixelSize(R.dimen.calendar_timeline_day_column_min);
        int defaultColor = ContextCompat.getColor(ctx, R.color.calendar_event_type_default);

        LocalDate[] weekDays = new LocalDate[7];
        for (int i = 0; i < 7; i++) {
            weekDays[i] = weekStart.plusDays(i);
        }

        List<List<CalendarListItem>> allDayByDay = new ArrayList<>();
        List<List<Timed>> timedByDay = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            allDayByDay.add(new ArrayList<>());
            timedByDay.add(new ArrayList<>());
        }
        for (CalendarListItem item : events) {
            Parsed p = parse(item);
            if (p == null) {
                continue;
            }
            int idx = (int) java.time.temporal.ChronoUnit.DAYS.between(weekStart, p.date);
            if (idx < 0 || idx > 6) {
                continue;
            }
            if (p.allDay) {
                allDayByDay.get(idx).add(item);
            } else {
                timedByDay.get(idx).add(new Timed(item, p.startMinute,
                        Math.min(p.startMinute + DEFAULT_DURATION_MIN, HOURS * 60)));
            }
        }
        for (List<Timed> list : timedByDay) {
            list.sort(Comparator.comparingInt(t -> t.startMin));
        }

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView hsv = new HorizontalScrollView(ctx);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setFillViewport(false);
        hsv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout outer = new LinearLayout(ctx);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        int reserved = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 24, ctx.getResources().getDisplayMetrics());
        int colW = Math.max(colMinPx, (screenW - reserved) / 7);
        outer.setLayoutParams(new HorizontalScrollView.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        for (int d = 0; d < 7; d++) {
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(colW, LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView head = new TextView(ctx);
            head.setGravity(Gravity.CENTER);
            head.setTextAppearance(R.style.TextAppearance_CourseSync_Caption);
            LocalDate day = weekDays[d];
            String dow = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, new Locale("ru"));
            if (dow != null && dow.length() > 0) {
                dow = dow.substring(0, 1).toUpperCase(Locale.getDefault()) + dow.substring(1);
            }
            head.setText(dow + "\n" + day.getDayOfMonth());
            int headPad = ctx.getResources().getDimensionPixelSize(R.dimen.grid_1);
            head.setPadding(headPad, headPad, headPad, headPad);
            head.setOnClickListener(v -> onDayHeaderClick.accept(day));
            col.addView(head);

            LinearLayout allDayRow = buildAllDayRow(ctx, allDayByDay.get(d), defaultColor, fragment, navigateActionId);
            LinearLayout.LayoutParams adLp = new LinearLayout.LayoutParams(colW, LinearLayout.LayoutParams.WRAP_CONTENT);
            allDayRow.setLayoutParams(adLp);
            col.addView(allDayRow);

            List<Timed> dayTimed = timedByDay.get(d);
            if (!dayTimed.isEmpty()) {
                LinearLayout stack = new LinearLayout(ctx);
                stack.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams stackLp = new LinearLayout.LayoutParams(
                        colW, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (!allDayByDay.get(d).isEmpty()) {
                    stackLp.topMargin = timelineBlockGapPx(ctx);
                }
                stack.setLayoutParams(stackLp);
                addTimedEventStack(ctx, stack, dayTimed, defaultColor, fragment, navigateActionId, colW);
                col.addView(stack);
            }

            outer.addView(col);
        }

        hsv.addView(outer);
        root.addView(hsv);
        host.addView(root);
    }

    private static LinearLayout buildAllDayRow(
            Context ctx,
            List<CalendarListItem> items,
            int defaultColor,
            Fragment fragment,
            int navigateActionId) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        int pad = ctx.getResources().getDimensionPixelSize(R.dimen.grid_1);
        int gap = timelineBlockGapPx(ctx);
        row.setPadding(0, 0, 0, items.isEmpty() ? 0 : pad);
        if (items.isEmpty()) {
            row.setVisibility(View.GONE);
            return row;
        }
        for (int i = 0; i < items.size(); i++) {
            CalendarListItem item = items.get(i);
            TextView chip = new TextView(ctx);
            chip.setText(buildCourseTypeNameLine(item));
            chip.setPadding(pad, pad / 2, pad, pad / 2);
            chip.setMaxLines(2);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 6, ctx.getResources().getDisplayMetrics()));
            int c = parseColor(item.eventColor, defaultColor);
            bg.setColor(Color.argb(55, Color.red(c), Color.green(c), Color.blue(c)));
            bg.setStroke(1, Color.argb(120, Color.red(c), Color.green(c), Color.blue(c)));
            chip.setBackground(bg);
            chip.setClickable(true);
            UUID id = item.id;
            chip.setOnClickListener(v -> navigateToEvent(fragment, navigateActionId, id));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = i > 0 ? gap : 0;
            chip.setLayoutParams(lp);
            row.addView(chip);
        }
        return row;
    }

    private static int timelineBlockGapPx(Context ctx) {
        return ctx.getResources().getDimensionPixelSize(R.dimen.calendar_timeline_block_gap);
    }

    private static void addTimedEventStack(
            Context ctx,
            LinearLayout parent,
            List<Timed> timed,
            int defaultColor,
            Fragment fragment,
            int navigateActionId,
            int widthPx) {
        if (timed.isEmpty()) {
            return;
        }
        int pad = ctx.getResources().getDimensionPixelSize(R.dimen.grid_1);
        int gap = timelineBlockGapPx(ctx);
        for (int i = 0; i < timed.size(); i++) {
            Timed t = timed.get(i);
            TextView tv = new TextView(ctx);
            tv.setText(buildEventLabel(t.item));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tv.setMaxLines(4);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setPadding(pad, pad / 2, pad, pad / 2);
            int c = parseColor(t.item.eventColor, defaultColor);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 4, ctx.getResources().getDisplayMetrics()));
            bg.setColor(Color.argb(200, Color.red(c), Color.green(c), Color.blue(c)));
            tv.setBackground(bg);
            tv.setTextColor(contrastTextColor(c));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = i > 0 ? gap : 0;
            tv.setLayoutParams(lp);
            UUID id = t.item.id;
            tv.setOnClickListener(v -> navigateToEvent(fragment, navigateActionId, id));
            parent.addView(tv);
        }
    }

    private static int contrastTextColor(int backgroundArgb) {
        int r = Color.red(backgroundArgb);
        int g = Color.green(backgroundArgb);
        int b = Color.blue(backgroundArgb);
        double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        return lum > 0.55 ? Color.BLACK : Color.WHITE;
    }

    private static String buildEventLabel(CalendarListItem item) {
        Parsed p = parse(item);
        String time = "";
        if (p != null && !p.allDay) {
            int h = p.startMinute / 60;
            int m = p.startMinute % 60;
            time = String.format(Locale.getDefault(), "%02d:%02d\n", h, m);
        }
        return time + buildCourseTypeNameLine(item);
    }

    @NonNull
    private static String buildCourseTypeNameLine(@NonNull CalendarListItem item) {
        StringBuilder sb = new StringBuilder();
        appendDotSegment(sb, item.courseName);
        appendDotSegment(sb, formatEventTypeForTimeline(item.eventType));
        appendDotSegment(sb, item.name);
        return sb.toString();
    }

    private static void appendDotSegment(@NonNull StringBuilder sb, @Nullable String part) {
        if (part == null) {
            return;
        }
        String t = part.trim();
        if (t.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(" · ");
        }
        sb.append(t);
    }

    @Nullable
    private static String formatEventTypeForTimeline(@Nullable String eventType) {
        if (eventType == null) {
            return null;
        }
        String t = eventType.trim();
        if (t.isEmpty()) {
            return null;
        }
        if ("Другое".equalsIgnoreCase(t)) {
            return null;
        }
        return t;
    }

    private static void navigateToEvent(Fragment fragment, int actionId, UUID eventId) {
        if (eventId == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putString("eventId", eventId.toString());
        NavHostFragment.findNavController(fragment).navigate(actionId, args);
    }

    @Nullable
    private static Parsed parse(CalendarListItem item) {
        if (item == null || item.date == null) {
            return null;
        }
        String raw = item.date.trim();
        if (raw.length() == 10 && raw.charAt(4) == '-' && raw.charAt(7) == '-') {
            try {
                LocalDate d = LocalDate.parse(raw);
                return new Parsed(d, true, 0);
            } catch (DateTimeParseException ignored) {
            }
        }
        if (raw.length() > 10 && isMidnightInSerializedZone(raw)) {
            try {
                LocalDate date;
                if (raw.endsWith("Z")) {
                    Instant i = Instant.parse(raw);
                    date = i.atZone(ZoneOffset.UTC).toLocalDate();
                } else {
                    OffsetDateTime odt = OffsetDateTime.parse(raw);
                    date = odt.toLocalDate();
                }
                return new Parsed(date, true, 0);
            } catch (DateTimeParseException ignored) {
            }
        }
        LocalDateTime ldt = parseToLocalDateTime(raw);
        if (ldt == null) {
            return null;
        }
        LocalDate date = ldt.toLocalDate();
        LocalTime time = ldt.toLocalTime();
        boolean allDay = time.getHour() == 0
                && time.getMinute() == 0
                && time.getSecond() == 0
                && time.getNano() == 0;
        int startMin = time.getHour() * 60 + time.getMinute();
        return new Parsed(date, allDay, startMin);
    }

    private static boolean isMidnightInSerializedZone(String raw) {
        try {
            if (raw.endsWith("Z")) {
                Instant i = Instant.parse(raw);
                return i.atZone(ZoneOffset.UTC).toLocalTime().equals(LocalTime.MIDNIGHT);
            }
            if (raw.length() > 10) {
                char c = raw.charAt(10);
                if (c == 'T') {
                    OffsetDateTime odt = OffsetDateTime.parse(raw);
                    return odt.toLocalTime().equals(LocalTime.MIDNIGHT);
                }
            }
        } catch (DateTimeParseException ignored) {
        }
        return false;
    }

    @Nullable
    private static LocalDateTime parseToLocalDateTime(String raw) {
        if (raw.length() < 10) {
            return null;
        }
        try {
            String z = raw;
            if (z.endsWith("Z")) {
                return java.time.Instant.parse(z).atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
            if (raw.length() > 10) {
                char c = raw.charAt(10);
                if (c == '+' || (c == '-' && raw.length() > 11)) {
                    return OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                }
            }
            if (raw.length() <= 10) {
                return LocalDate.parse(raw.substring(0, 10)).atStartOfDay();
            }
            String core = raw;
            int tzPlus = core.indexOf('+', 10);
            if (tzPlus > 0) {
                core = core.substring(0, tzPlus);
            }
            if (core.length() >= 19) {
                return LocalDateTime.parse(core.substring(0, 19));
            }
            if (core.length() >= 16) {
                return LocalDateTime.parse(core.substring(0, 16));
            }
            return LocalDate.parse(core.substring(0, 10)).atStartOfDay();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static int parseColor(String hex, int fallback) {
        if (hex == null) {
            return fallback;
        }
        String s = hex.trim();
        if (s.isEmpty()) {
            return fallback;
        }
        try {
            if (!s.startsWith("#")) {
                s = "#" + s;
            }
            return Color.parseColor(s);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static final class Parsed {
        final LocalDate date;
        final boolean allDay;
        final int startMinute;

        Parsed(LocalDate date, boolean allDay, int startMinute) {
            this.date = date;
            this.allDay = allDay;
            this.startMinute = startMinute;
        }
    }

    private static final class Timed {
        final CalendarListItem item;
        final int startMin;
        final int endMin;

        Timed(CalendarListItem item, int startMin, int endMin) {
            this.item = item;
            this.startMin = startMin;
            this.endMin = endMin;
        }
    }

}
