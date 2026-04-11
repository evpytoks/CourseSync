package ru.katevpy.coursesync.calendar;

import android.content.res.TypedArray;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CalendarListItem;

public final class CalendarEventListBinder {

    private CalendarEventListBinder() {
    }

    public static void bind(
            @NonNull LinearLayout calendarEventsList,
            @NonNull List<CalendarListItem> events,
            @NonNull Fragment fragment,
            @NonNull Consumer<UUID> onToggleEventDone,
            int navigateToEventDetailActionId) {
        calendarEventsList.removeAllViews();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault());
        DateTimeFormatter dateTimeFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault());
        int paddingPx = fragment.getResources().getDimensionPixelSize(R.dimen.grid_2);
        int[] attrs = new int[]{android.R.attr.selectableItemBackground};
        TypedArray ta = fragment.requireContext().getTheme().obtainStyledAttributes(attrs);
        int rippleResId = ta.getResourceId(0, 0);
        ta.recycle();
        int[] attrsBorderless = new int[]{android.R.attr.selectableItemBackgroundBorderless};
        TypedArray taB = fragment.requireContext().getTheme().obtainStyledAttributes(attrsBorderless);
        int rippleBorderlessId = taB.getResourceId(0, 0);
        taB.recycle();
        int hitSize = fragment.getResources().getDimensionPixelSize(R.dimen.calendar_event_done_touch_size);
        for (CalendarListItem item : events) {
            String line = formatEventLine(item, dateFmt, dateTimeFmt);
            LinearLayout row = new LinearLayout(fragment.requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            ImageButton doneBtn = new ImageButton(fragment.requireContext());
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(hitSize, hitSize);
            btnLp.gravity = Gravity.CENTER_VERTICAL;
            btnLp.setMarginEnd(fragment.getResources().getDimensionPixelSize(R.dimen.grid_1));
            doneBtn.setLayoutParams(btnLp);
            doneBtn.setBackgroundResource(rippleBorderlessId);
            doneBtn.setImageResource(item.isDone
                    ? R.drawable.calendar_event_circle_done
                    : R.drawable.calendar_event_circle_empty);
            doneBtn.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
            doneBtn.setContentDescription(fragment.getString(R.string.calendar_event_toggle_done));
            doneBtn.setPadding(0, 0, 0, 0);

            TextView tv = new TextView(fragment.requireContext());
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textLp.gravity = Gravity.CENTER_VERTICAL;
            tv.setLayoutParams(textLp);
            tv.setText(line);
            tv.setTextAppearance(R.style.TextAppearance_CourseSync_Body);
            tv.setPadding(0, paddingPx, paddingPx, paddingPx);
            tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_right, 0);
            tv.setCompoundDrawablePadding(fragment.getResources().getDimensionPixelSize(R.dimen.grid_1));
            tv.setBackgroundResource(rippleResId);
            tv.setClickable(true);
            tv.setFocusable(true);
            UUID eventId = item.id;
            doneBtn.setOnClickListener(v -> onToggleEventDone.accept(eventId));
            tv.setOnClickListener(v -> {
                if (eventId != null) {
                    Bundle args = new Bundle();
                    args.putString("eventId", eventId.toString());
                    NavHostFragment.findNavController(fragment)
                            .navigate(navigateToEventDetailActionId, args);
                }
            });
            row.addView(doneBtn);
            row.addView(tv);
            calendarEventsList.addView(row);
        }
    }

    private static void appendPart(StringBuilder sb, @Nullable String part) {
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
    private static String formatEventTypeForLine(@Nullable String eventType) {
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

    private static String formatEventLine(CalendarListItem item, DateTimeFormatter dateFmt, DateTimeFormatter dateTimeFmt) {
        StringBuilder sb = new StringBuilder();
        appendPart(sb, formatEventDateTimeForLine(item.date, dateFmt, dateTimeFmt));
        appendPart(sb, item.courseName);
        appendPart(sb, formatEventTypeForLine(item.eventType));
        appendPart(sb, item.name);
        appendPart(sb, item.groupName);
        return sb.length() > 0 ? sb.toString() : "";
    }

    @Nullable
    private static String formatEventDateTimeForLine(
            @Nullable String dateStr,
            DateTimeFormatter dateFmt,
            DateTimeFormatter dateTimeFmt) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        String core = dateStr;
        if (core.endsWith("Z")) {
            core = core.substring(0, core.length() - 1);
        }
        int tzPlus = core.indexOf('+', 10);
        if (tzPlus > 0) {
            core = core.substring(0, tzPlus);
        }
        if (core.length() < 10) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(core.substring(0, 10));
            if (core.length() <= 10) {
                return date.format(dateFmt);
            }
            String afterT = core.substring(11);
            if (afterT.isEmpty()) {
                return date.format(dateFmt);
            }
            String timePart = afterT.length() >= 5 ? afterT.substring(0, 5) : afterT;
            String[] parts = timePart.split(":");
            if (parts.length >= 2) {
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                if (h == 0 && m == 0) {
                    return date.format(dateFmt);
                }
                return LocalDateTime.of(date, LocalTime.of(h, m)).format(dateTimeFmt);
            }
            return date.format(dateFmt);
        } catch (DateTimeParseException | NumberFormatException e) {
            return null;
        }
    }
}
