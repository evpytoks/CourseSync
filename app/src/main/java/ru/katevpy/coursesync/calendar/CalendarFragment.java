package ru.katevpy.coursesync.calendar;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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

import com.google.android.material.button.MaterialButtonToggleGroup;
import ru.katevpy.coursesync.ui.ErrorUi;
import com.kizitonwose.calendar.core.CalendarDay;
import com.kizitonwose.calendar.core.CalendarMonth;
import com.kizitonwose.calendar.core.DayPosition;
import com.kizitonwose.calendar.view.CalendarView;
import com.kizitonwose.calendar.view.MonthDayBinder;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
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

    private enum ViewMode {
        MONTH, WEEK, DAY
    }

    private CalendarView calendarView;
    private FrameLayout scheduleHost;
    private View calendarViewHost;
    private TextView calendarEventsTitle;
    private View calendarNoGroupMessage;
    private View calendarScroll;
    private LinearLayout calendarEventsList;
    private TextView calendarMonthYear;
    private ImageButton btnMonthPrev;
    private ImageButton btnMonthNext;
    private MaterialButtonToggleGroup viewModeGroup;
    private CalendarViewModel viewModel;
    private final Map<String, List<CalendarListItem>> eventsByDay = new HashMap<>();
    private YearMonth currentDisplayMonth;
    private ViewMode viewMode = ViewMode.MONTH;
    private LocalDate selectedDayForDayView;
    private java.time.DayOfWeek firstDayOfWeek;
    @Nullable
    private LocalDate weekTimelineStart;
    private List<CalendarListItem> lastLoadedEvents = Collections.emptyList();
    private boolean suppressViewModeCallback;

    private final DateTimeFormatter dayTitleFormatter =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", new Locale("ru"));
    private final DateTimeFormatter weekSameMonthFormatter =
            DateTimeFormatter.ofPattern("d", new Locale("ru"));
    private final DateTimeFormatter weekMonthFormatter =
            DateTimeFormatter.ofPattern("MMMM", new Locale("ru"));
    private final DateTimeFormatter weekFullFormatter =
            DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("ru"));

    public CalendarFragment() {
        super(R.layout.fragment_calendar);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        calendarView = view.findViewById(R.id.calendarView);
        scheduleHost = view.findViewById(R.id.calendarScheduleHost);
        calendarViewHost = view.findViewById(R.id.calendarViewHost);
        calendarNoGroupMessage = view.findViewById(R.id.calendarNoGroupMessage);
        calendarScroll = view.findViewById(R.id.calendarScroll);
        calendarEventsList = view.findViewById(R.id.calendarEventsList);
        calendarEventsTitle = view.findViewById(R.id.calendarEventsTitle);
        calendarMonthYear = view.findViewById(R.id.calendarMonthYear);
        btnMonthPrev = view.findViewById(R.id.btnMonthPrev);
        btnMonthNext = view.findViewById(R.id.btnMonthNext);
        viewModeGroup = view.findViewById(R.id.calendarViewModeGroup);

        viewModel = new ViewModelProvider(this, new CalendarViewModelFactory()).get(CalendarViewModel.class);

        calendarNoGroupMessage.setVisibility(View.GONE);
        calendarScroll.setVisibility(View.VISIBLE);

        int calFirst = java.util.Calendar.getInstance().getFirstDayOfWeek();
        firstDayOfWeek = calFirst == java.util.Calendar.SUNDAY
                ? java.time.DayOfWeek.SUNDAY
                : java.time.DayOfWeek.of(calFirst - 1);

        LocalDate today = LocalDate.now();
        selectedDayForDayView = today;

        calendarView.setOnTouchListener((v, event) -> event.getAction() == MotionEvent.ACTION_MOVE);

        btnMonthPrev.setOnClickListener(v -> onNavPrev());
        btnMonthNext.setOnClickListener(v -> onNavNext());

        viewModeGroup.addOnButtonCheckedListener(this::onViewModeButtonChecked);

        YearMonth nowYm = YearMonth.now();
        currentDisplayMonth = nowYm;
        setupCalendar();
        viewMode = ViewMode.MONTH;
        applyViewMode();
        updateNavTitleAndDescriptions();
        updateEventsSectionTitle();
        viewModel.setCurrentMonth(nowYm.getYear(), nowYm.getMonthValue() - 1);
        viewModel.loadEventsForMonth(nowYm.getYear(), nowYm.getMonthValue() - 1);

        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);
        viewModel.getToggleEventFailure().observe(getViewLifecycleOwner(), this::onToggleEventFailure);
    }

    private void onViewModeButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
        if (!isChecked || suppressViewModeCallback) {
            return;
        }
        ViewMode from = viewMode;
        if (checkedId == R.id.btnViewModeMonth) {
            goToMonthMode(from);
        } else if (checkedId == R.id.btnViewModeWeek) {
            goToWeekMode(from);
        } else if (checkedId == R.id.btnViewModeDay) {
            goToDayMode();
        }
    }

    private void goToMonthMode(ViewMode from) {
        viewMode = ViewMode.MONTH;
        if (from == ViewMode.WEEK && weekTimelineStart != null) {
            currentDisplayMonth = YearMonth.from(weekTimelineStart);
            calendarView.scrollToMonth(currentDisplayMonth);
        } else if (from == ViewMode.DAY) {
            currentDisplayMonth = YearMonth.from(selectedDayForDayView);
            calendarView.scrollToMonth(currentDisplayMonth);
        }
        applyViewMode();
        viewModel.loadEventsForMonth(currentDisplayMonth.getYear(), currentDisplayMonth.getMonthValue() - 1);
        updateNavTitleAndDescriptions();
        updateEventsSectionTitle();
        calendarView.post(this::syncCalendarGridHeight);
    }

    private void goToWeekMode(ViewMode from) {
        viewMode = ViewMode.WEEK;
        LocalDate anchor;
        if (from == ViewMode.DAY) {
            anchor = selectedDayForDayView;
        } else {
            LocalDate today = LocalDate.now();
            if (YearMonth.from(today).equals(currentDisplayMonth)) {
                anchor = today;
            } else {
                anchor = currentDisplayMonth.atDay(1);
            }
        }
        weekTimelineStart = startOfWeekContaining(anchor, firstDayOfWeek);
        applyViewMode();
        viewModel.loadEventsForRange(weekTimelineStart, weekTimelineStart.plusDays(6));
        updateNavTitleAndDescriptions();
        updateEventsSectionTitle();
    }

    private void goToDayMode() {
        viewMode = ViewMode.DAY;
        applyViewMode();
        viewModel.loadEventsForRange(selectedDayForDayView, selectedDayForDayView);
        updateNavTitleAndDescriptions();
        updateEventsSectionTitle();
    }

    private void openDayView(@NonNull LocalDate date) {
        selectedDayForDayView = date;
        suppressViewModeCallback = true;
        viewModeGroup.check(R.id.btnViewModeDay);
        suppressViewModeCallback = false;
        viewMode = ViewMode.DAY;
        applyViewMode();
        viewModel.loadEventsForRange(date, date);
        updateNavTitleAndDescriptions();
        updateEventsSectionTitle();
    }

    private void applyViewMode() {
        ViewGroup.LayoutParams hlp = calendarViewHost.getLayoutParams();
        switch (viewMode) {
            case MONTH:
                calendarViewHost.setVisibility(View.VISIBLE);
                calendarView.setVisibility(View.VISIBLE);
                if (scheduleHost != null) {
                    scheduleHost.setVisibility(View.GONE);
                    CalendarScheduleTimelineBinder.clear(scheduleHost);
                }
                calendarView.post(this::syncCalendarGridHeight);
                break;
            case WEEK:
            case DAY:
            default:
                calendarViewHost.setVisibility(View.VISIBLE);
                calendarView.setVisibility(View.GONE);
                if (scheduleHost != null) {
                    scheduleHost.setVisibility(View.VISIBLE);
                }
                if (hlp != null) {
                    hlp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    calendarViewHost.setLayoutParams(hlp);
                }
                refreshScheduleTimeline();
                break;
        }
    }

    private void onNavPrev() {
        switch (viewMode) {
            case MONTH:
                moveMonth(-1);
                break;
            case WEEK:
                if (weekTimelineStart == null) {
                    weekTimelineStart = startOfWeekContaining(LocalDate.now(), firstDayOfWeek);
                }
                weekTimelineStart = weekTimelineStart.minusWeeks(1);
                viewModel.loadEventsForRange(weekTimelineStart, weekTimelineStart.plusDays(6));
                updateNavTitleAndDescriptions();
                break;
            case DAY:
                selectedDayForDayView = selectedDayForDayView.minusDays(1);
                viewModel.loadEventsForRange(selectedDayForDayView, selectedDayForDayView);
                updateNavTitleAndDescriptions();
                break;
            default:
                break;
        }
    }

    private void onNavNext() {
        switch (viewMode) {
            case MONTH:
                moveMonth(1);
                break;
            case WEEK:
                if (weekTimelineStart == null) {
                    weekTimelineStart = startOfWeekContaining(LocalDate.now(), firstDayOfWeek);
                }
                weekTimelineStart = weekTimelineStart.plusWeeks(1);
                viewModel.loadEventsForRange(weekTimelineStart, weekTimelineStart.plusDays(6));
                updateNavTitleAndDescriptions();
                break;
            case DAY:
                selectedDayForDayView = selectedDayForDayView.plusDays(1);
                viewModel.loadEventsForRange(selectedDayForDayView, selectedDayForDayView);
                updateNavTitleAndDescriptions();
                break;
            default:
                break;
        }
    }

    private void updateEventsSectionTitle() {
        if (calendarEventsTitle == null) {
            return;
        }
        switch (viewMode) {
            case MONTH:
                calendarEventsTitle.setText(R.string.calendar_events_title);
                break;
            case WEEK:
                calendarEventsTitle.setText(R.string.calendar_events_week);
                break;
            case DAY:
            default:
                calendarEventsTitle.setText(R.string.calendar_events_day);
                break;
        }
    }

    private void updateNavTitleAndDescriptions() {
        switch (viewMode) {
            case MONTH:
                updateMonthYearTitle();
                btnMonthPrev.setContentDescription(getString(R.string.calendar_prev_month));
                btnMonthNext.setContentDescription(getString(R.string.calendar_next_month));
                break;
            case WEEK:
                if (weekTimelineStart != null) {
                    updateNavTitleForWeekRange(weekTimelineStart, weekTimelineStart.plusDays(6));
                }
                btnMonthPrev.setContentDescription(getString(R.string.calendar_prev_week));
                btnMonthNext.setContentDescription(getString(R.string.calendar_next_week));
                break;
            case DAY:
            default:
                String cap = capitalizeRussianDayTitle(selectedDayForDayView.format(dayTitleFormatter));
                calendarMonthYear.setText(cap);
                btnMonthPrev.setContentDescription(getString(R.string.calendar_prev_day));
                btnMonthNext.setContentDescription(getString(R.string.calendar_next_day));
                break;
        }
    }

    private static String capitalizeRussianDayTitle(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }

    private void updateNavTitleForWeekRange(@NonNull LocalDate start, @NonNull LocalDate end) {
        if (start.getMonth().equals(end.getMonth()) && start.getYear() == end.getYear()) {
            String m = start.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru"));
            if (m != null && !m.isEmpty()) {
                m = m.substring(0, 1).toUpperCase(Locale.getDefault()) + m.substring(1);
            }
            calendarMonthYear.setText(
                    start.format(weekSameMonthFormatter) + "–" + end.format(weekSameMonthFormatter)
                            + " " + m + " " + start.getYear());
        } else if (start.getYear() == end.getYear()) {
            String m1 = start.format(weekMonthFormatter);
            String m2 = end.format(weekMonthFormatter);
            calendarMonthYear.setText(
                    start.format(weekSameMonthFormatter) + " " + m1 + " – "
                            + end.format(weekSameMonthFormatter) + " " + m2 + " " + start.getYear());
        } else {
            calendarMonthYear.setText(
                    start.format(weekFullFormatter) + " – " + end.format(weekFullFormatter));
        }
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
        if (currentDisplayMonth == null) {
            return;
        }
        currentDisplayMonth = currentDisplayMonth.plusMonths(delta);
        calendarView.smoothScrollToMonth(currentDisplayMonth);
        calendarView.post(this::syncCalendarGridHeight);
        viewModel.setCurrentMonth(currentDisplayMonth.getYear(), currentDisplayMonth.getMonthValue() - 1);
        viewModel.loadEventsForMonth(currentDisplayMonth.getYear(), currentDisplayMonth.getMonthValue() - 1);
        updateMonthYearTitle();
    }

    private void updateMonthYearTitle() {
        if (currentDisplayMonth == null || calendarMonthYear == null) {
            return;
        }
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
                container.getView().setOnClickListener(v -> {
                    if (day.getPosition() == DayPosition.MonthDate) {
                        openDayView(date);
                    }
                });
            }
        });
        calendarView.setup(start, end, firstDayOfWeek);
        calendarView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    syncCalendarGridHeight();
                    if (viewMode == ViewMode.MONTH) {
                        CalendarMonth cm = calendarView.findFirstVisibleMonth();
                        if (cm != null) {
                            YearMonth ym = cm.getYearMonth();
                            if (!ym.equals(currentDisplayMonth)) {
                                currentDisplayMonth = ym;
                                viewModel.setCurrentMonth(ym.getYear(), ym.getMonthValue() - 1);
                                viewModel.loadEventsForMonth(ym.getYear(), ym.getMonthValue() - 1);
                                updateMonthYearTitle();
                            }
                        }
                    }
                }
            }
        });
        calendarView.scrollToMonth(now);
        calendarView.post(this::syncCalendarGridHeight);
    }

    private void syncCalendarGridHeight() {
        if (viewMode != ViewMode.MONTH || calendarView == null || calendarViewHost == null) {
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
        if (result == null) {
            return;
        }

        if (result instanceof Result.Success) {
            List<CalendarListItem> list = ((Result.Success<List<CalendarListItem>>) result).data;
            lastLoadedEvents = list != null ? new ArrayList<>(list) : Collections.emptyList();
            rebuildEventsByDay(list);
            if (viewMode == ViewMode.MONTH) {
                Integer year = viewModel.getCurrentYear().getValue();
                Integer month = viewModel.getCurrentMonth().getValue();
                if (year != null && month != null) {
                    calendarView.notifyMonthChanged(YearMonth.of(year, month + 1));
                }
                calendarView.post(this::syncCalendarGridHeight);
            } else {
                refreshScheduleTimeline();
            }
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
        if (raw == null || raw.length() < 10) {
            return null;
        }
        try {
            return raw.substring(0, 10);
        } catch (Exception e) {
            return null;
        }
    }

    private void renderEventsList(List<CalendarListItem> events) {
        CalendarEventListBinder.bind(
                calendarEventsList,
                events,
                this,
                id -> viewModel.toggleEventDone(id),
                R.id.action_calendarFragment_to_calendarEventDetailFragment);
    }

    private static LocalDate startOfWeekContaining(@NonNull LocalDate d, @NonNull java.time.DayOfWeek firstDow) {
        int v = d.getDayOfWeek().getValue();
        int f = firstDow.getValue();
        int diff = (v - f + 7) % 7;
        return d.minusDays(diff);
    }

    private void refreshScheduleTimeline() {
        if (scheduleHost == null) {
            return;
        }
        if (viewMode == ViewMode.DAY) {
            CalendarScheduleTimelineBinder.bindDay(
                    this,
                    scheduleHost,
                    selectedDayForDayView,
                    lastLoadedEvents,
                    R.id.action_calendarFragment_to_calendarEventDetailFragment);
        } else if (viewMode == ViewMode.WEEK) {
            LocalDate ws = weekTimelineStart != null
                    ? weekTimelineStart
                    : startOfWeekContaining(LocalDate.now(), firstDayOfWeek);
            CalendarScheduleTimelineBinder.bindWeek(
                    this,
                    scheduleHost,
                    ws,
                    lastLoadedEvents,
                    R.id.action_calendarFragment_to_calendarEventDetailFragment,
                    this::openDayView);
        } else {
            CalendarScheduleTimelineBinder.clear(scheduleHost);
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

}
