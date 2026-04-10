package ru.katevpy.coursesync.calendar;

import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.navigation.fragment.NavHostFragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import ru.katevpy.coursesync.ui.ErrorUi;
import com.kizitonwose.calendar.core.CalendarDay;
import com.kizitonwose.calendar.core.CalendarMonth;
import com.kizitonwose.calendar.core.DayPosition;
import com.kizitonwose.calendar.view.CalendarView;
import com.kizitonwose.calendar.view.MonthDayBinder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CalendarListItem;
import ru.katevpy.coursesync.shared.util.Result;

public class CalendarFragment extends Fragment {

    private CalendarView calendarView;
    private View calendarViewHost;
    private View calendarNoGroupMessage;
    private View calendarScroll;
    private LinearLayout calendarEventsList;
    private TextView calendarMonthYear;
    private ImageButton btnMonthPrev;
    private ImageButton btnMonthNext;
    private CalendarViewModel viewModel;
    private final Map<String, List<CalendarListItem>> eventsByDay = new HashMap<>();
    private YearMonth currentDisplayMonth;

    public CalendarFragment() {
        super(R.layout.fragment_calendar);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        calendarView = view.findViewById(R.id.calendarView);
        calendarViewHost = view.findViewById(R.id.calendarViewHost);
        calendarNoGroupMessage = view.findViewById(R.id.calendarNoGroupMessage);
        calendarScroll = view.findViewById(R.id.calendarScroll);
        calendarEventsList = view.findViewById(R.id.calendarEventsList);
        calendarMonthYear = view.findViewById(R.id.calendarMonthYear);
        btnMonthPrev = view.findViewById(R.id.btnMonthPrev);
        btnMonthNext = view.findViewById(R.id.btnMonthNext);

        viewModel = new ViewModelProvider(this, new CalendarViewModelFactory()).get(CalendarViewModel.class);

        calendarNoGroupMessage.setVisibility(View.GONE);
        calendarScroll.setVisibility(View.VISIBLE);

        calendarView.setOnTouchListener((v, event) -> event.getAction() == MotionEvent.ACTION_MOVE);

        btnMonthPrev.setOnClickListener(v -> moveMonth(-1));
        btnMonthNext.setOnClickListener(v -> moveMonth(1));

        YearMonth now = YearMonth.now();
        currentDisplayMonth = now;
        setupCalendar();
        updateMonthYearTitle();
        viewModel.setCurrentMonth(now.getYear(), now.getMonthValue() - 1);
        viewModel.loadEventsForMonth(now.getYear(), now.getMonthValue() - 1);

        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);
        viewModel.getToggleEventFailure().observe(getViewLifecycleOwner(), this::onToggleEventFailure);
    }

    private void onToggleEventFailure(@Nullable Result<Void> r) {
        if (r == null) {
            return;
        }
        if (r instanceof Result.HttpError) {
            int code = ((Result.HttpError<?>) r).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                viewModel.consumeToggleEventFailure();
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.calendar_load_error, ErrorUi.Duration.SHORT);
            } else {
                ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
            }
        } else if (r instanceof Result.NetworkError) {
            ErrorUi.show(this, R.string.network_error, ErrorUi.Duration.SHORT);
        } else {
            ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
        }
        viewModel.consumeToggleEventFailure();
    }

    private void moveMonth(int delta) {
        if (currentDisplayMonth == null) return;
        currentDisplayMonth = currentDisplayMonth.plusMonths(delta);
        calendarView.smoothScrollToMonth(currentDisplayMonth);
        calendarView.post(this::syncCalendarGridHeight);
        viewModel.setCurrentMonth(currentDisplayMonth.getYear(), currentDisplayMonth.getMonthValue() - 1);
        viewModel.loadEventsForMonth(currentDisplayMonth.getYear(), currentDisplayMonth.getMonthValue() - 1);
        updateMonthYearTitle();
    }

    private void updateMonthYearTitle() {
        if (currentDisplayMonth == null || calendarMonthYear == null) return;
        String monthName = currentDisplayMonth.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru"));
        if (monthName != null && !monthName.isEmpty()) {
            monthName = monthName.substring(0, 1).toUpperCase(Locale.getDefault()) + monthName.substring(1);
        }
        calendarMonthYear.setText(monthName + " " + currentDisplayMonth.getYear());
    }

    private void setupCalendar() {
        YearMonth now = YearMonth.now();
        YearMonth start = now.minusMonths(24);
        YearMonth end = now.plusMonths(24);
        int calFirst = java.util.Calendar.getInstance().getFirstDayOfWeek();
        java.time.DayOfWeek firstDay = calFirst == java.util.Calendar.SUNDAY ? java.time.DayOfWeek.SUNDAY : java.time.DayOfWeek.of(calFirst - 1);
        calendarView.setDayBinder(new MonthDayBinder<CalendarDayViewContainer>() {
            @Override
            public CalendarDayViewContainer create(View itemView) {
                return new CalendarDayViewContainer(itemView);
            }

            @Override
            public void bind(CalendarDayViewContainer container, CalendarDay day) {
                LocalDate date = day.getDate();
                container.dayText.setText(String.valueOf(date.getDayOfMonth()));
                String key = date.getYear() + "-" + pad(date.getMonthValue()) + "-" + pad(date.getDayOfMonth());
                boolean isMonthDate = day.getPosition() == DayPosition.MonthDate;
                bindDayEventDots(container, key, isMonthDate);
                if (!isMonthDate) {
                    container.dayText.setAlpha(0.3f);
                } else {
                    container.dayText.setAlpha(1f);
                }
            }
        });
        calendarView.setup(start, end, firstDay);
        calendarView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    syncCalendarGridHeight();
                }
            }
        });
        calendarView.scrollToMonth(now);
        calendarView.post(this::syncCalendarGridHeight);
    }

    private void syncCalendarGridHeight() {
        if (calendarView == null || calendarViewHost == null) {
            return;
        }
        int w = calendarView.getWidth();
        if (w <= 0) {
            calendarView.post(this::syncCalendarGridHeight);
            return;
        }
        CalendarMonth month = calendarView.findFirstVisibleMonth();
        if (month == null) {
            calendarView.post(this::syncCalendarGridHeight);
            return;
        }
        int cell = Math.max(1, w / 7);
        int rows = month.getWeekDays().size();
        if (rows <= 0) {
            rows = 6;
        }
        int h = cell * rows;
        ViewGroup.LayoutParams lp = calendarViewHost.getLayoutParams();
        if (lp.height == h) {
            return;
        }
        lp.height = h;
        calendarViewHost.setLayoutParams(lp);
    }

    private static String pad(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    private void rebuildEventsByDay(@Nullable List<CalendarListItem> list) {
        eventsByDay.clear();
        if (list == null) {
            return;
        }
        for (CalendarListItem item : list) {
            String dayKey = dayKeyFromEventDate(item.date);
            if (dayKey == null) {
                continue;
            }
            eventsByDay.computeIfAbsent(dayKey, k -> new ArrayList<>()).add(item);
        }
        for (List<CalendarListItem> dayList : eventsByDay.values()) {
            dayList.sort(Comparator.comparing(it -> it.date != null ? it.date : ""));
        }
    }

    private void bindDayEventDots(CalendarDayViewContainer container, String dayKey, boolean isMonthDate) {
        container.eventDotsRow.removeAllViews();
        if (!isMonthDate) {
            container.eventDotsRow.setVisibility(View.GONE);
            return;
        }
        List<CalendarListItem> dayEvents = eventsByDay.get(dayKey);
        if (dayEvents == null || dayEvents.isEmpty()) {
            container.eventDotsRow.setVisibility(View.GONE);
            return;
        }
        container.eventDotsRow.setVisibility(View.VISIBLE);
        android.content.Context ctx = container.getView().getContext();
        int dotPx = ctx.getResources().getDimensionPixelSize(R.dimen.calendar_day_event_dot_size);
        int gapPx = ctx.getResources().getDimensionPixelSize(R.dimen.calendar_day_event_dot_gap);
        int defaultArgb = ContextCompat.getColor(ctx, R.color.calendar_event_type_default);
        final int maxDots = 4;
        int n = Math.min(dayEvents.size(), maxDots);
        for (int i = 0; i < n; i++) {
            View dot = new View(ctx);
            int c = parseEventTypeColorArgb(dayEvents.get(i).eventColor, defaultArgb);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(c);
            dot.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotPx, dotPx);
            if (i > 0) {
                lp.setMarginStart(gapPx);
            }
            container.eventDotsRow.addView(dot, lp);
        }
    }

    private void onLoadResult(@Nullable Result<List<CalendarListItem>> result) {
        if (result == null) return;

        if (result instanceof Result.Success) {
            List<CalendarListItem> list = ((Result.Success<List<CalendarListItem>>) result).data;
            rebuildEventsByDay(list);
            Integer year = viewModel.getCurrentYear().getValue();
            Integer month = viewModel.getCurrentMonth().getValue();
            if (year != null && month != null) {
                calendarView.notifyMonthChanged(YearMonth.of(year, month + 1));
            }
            calendarView.post(this::syncCalendarGridHeight);
            renderEventsList(list);
            return;
        }

        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<List<CalendarListItem>>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.calendar_load_error, ErrorUi.Duration.SHORT);
                return;
            }
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
    }

    @Nullable
    private static String dayKeyFromEventDate(@Nullable String raw) {
        if (raw == null || raw.length() < 10) return null;
        try {
            return raw.substring(0, 10);
        } catch (Exception e) {
            return null;
        }
    }

    private void renderEventsList(List<CalendarListItem> events) {
        calendarEventsList.removeAllViews();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault());
        DateTimeFormatter dateTimeFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault());
        int paddingPx = getResources().getDimensionPixelSize(R.dimen.grid_2);
        int[] attrs = new int[]{android.R.attr.selectableItemBackground};
        TypedArray ta = requireContext().getTheme().obtainStyledAttributes(attrs);
        int rippleResId = ta.getResourceId(0, 0);
        ta.recycle();
        int[] attrsBorderless = new int[]{android.R.attr.selectableItemBackgroundBorderless};
        TypedArray taB = requireContext().getTheme().obtainStyledAttributes(attrsBorderless);
        int rippleBorderlessId = taB.getResourceId(0, 0);
        taB.recycle();
        int hitSize = getResources().getDimensionPixelSize(R.dimen.calendar_event_done_touch_size);
        for (CalendarListItem item : events) {
            String line = formatEventLine(item, dateFmt, dateTimeFmt);
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            ImageButton doneBtn = new ImageButton(requireContext());
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(hitSize, hitSize);
            btnLp.gravity = Gravity.CENTER_VERTICAL;
            btnLp.setMarginEnd(getResources().getDimensionPixelSize(R.dimen.grid_1));
            doneBtn.setLayoutParams(btnLp);
            doneBtn.setBackgroundResource(rippleBorderlessId);
            doneBtn.setImageResource(item.isDone
                    ? R.drawable.calendar_event_circle_done
                    : R.drawable.calendar_event_circle_empty);
            doneBtn.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
            doneBtn.setContentDescription(getString(R.string.calendar_event_toggle_done));
            doneBtn.setPadding(0, 0, 0, 0);

            TextView tv = new TextView(requireContext());
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textLp.gravity = Gravity.CENTER_VERTICAL;
            tv.setLayoutParams(textLp);
            tv.setText(line);
            tv.setTextAppearance(R.style.TextAppearance_CourseSync_Body);
            tv.setPadding(0, paddingPx, paddingPx, paddingPx);
            tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_right, 0);
            tv.setCompoundDrawablePadding(getResources().getDimensionPixelSize(R.dimen.grid_1));
            tv.setBackgroundResource(rippleResId);
            tv.setClickable(true);
            tv.setFocusable(true);
            UUID eventId = item.id;
            doneBtn.setOnClickListener(v -> viewModel.toggleEventDone(eventId));
            tv.setOnClickListener(v -> {
                if (eventId != null) {
                    Bundle args = new Bundle();
                    args.putString("eventId", eventId.toString());
                    NavHostFragment.findNavController(CalendarFragment.this)
                            .navigate(R.id.action_calendarFragment_to_calendarEventDetailFragment, args);
                }
            });
            row.addView(doneBtn);
            row.addView(tv);
            calendarEventsList.addView(row);
        }
    }

    private static int parseEventTypeColorArgb(@Nullable String hex, int fallbackArgb) {
        if (hex == null) {
            return fallbackArgb;
        }
        String s = hex.trim();
        if (s.isEmpty()) {
            return fallbackArgb;
        }
        try {
            if (!s.startsWith("#")) {
                s = "#" + s;
            }
            return Color.parseColor(s);
        } catch (IllegalArgumentException e) {
            return fallbackArgb;
        }
    }

    @Nullable
    private static String formatEventTypeForLine(@Nullable String eventType) {
        if (eventType == null) return null;
        String t = eventType.trim();
        if (t.isEmpty()) return null;
        if ("Другое".equalsIgnoreCase(t)) return null;
        return t;
    }

    private static void appendPart(StringBuilder sb, @Nullable String part) {
        if (part == null) return;
        String t = part.trim();
        if (t.isEmpty()) return;
        if (sb.length() > 0) {
            sb.append(" · ");
        }
        sb.append(t);
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
    private static String formatEventDateTimeForLine(@Nullable String dateStr, DateTimeFormatter dateFmt, DateTimeFormatter dateTimeFmt) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        String core = dateStr;
        if (core.endsWith("Z")) {
            core = core.substring(0, core.length() - 1);
        }
        int tzPlus = core.indexOf('+', 10);
        if (tzPlus > 0) {
            core = core.substring(0, tzPlus);
        }
        if (core.length() < 10) return null;
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
