package ru.katevpy.coursesync.calendar;

import android.content.res.TypedArray;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.navigation.fragment.NavHostFragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import ru.katevpy.coursesync.ui.ErrorUi;
import com.kizitonwose.calendar.core.CalendarDay;
import com.kizitonwose.calendar.core.DayPosition;
import com.kizitonwose.calendar.view.CalendarView;
import com.kizitonwose.calendar.view.MonthDayBinder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.SharedGroupViewModel;
import ru.katevpy.coursesync.shared.dto.CalendarListItem;
import ru.katevpy.coursesync.shared.util.Result;

public class CalendarFragment extends Fragment {

    private CalendarView calendarView;
    private View calendarNoGroupMessage;
    private View calendarScroll;
    private LinearLayout calendarEventsList;
    private TextView calendarMonthYear;
    private ImageButton btnMonthPrev;
    private ImageButton btnMonthNext;
    private CalendarViewModel viewModel;
    private Set<String> daysWithEvents = new HashSet<>();
    private YearMonth currentDisplayMonth;

    public CalendarFragment() {
        super(R.layout.fragment_calendar);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        calendarView = view.findViewById(R.id.calendarView);
        calendarNoGroupMessage = view.findViewById(R.id.calendarNoGroupMessage);
        calendarScroll = view.findViewById(R.id.calendarScroll);
        calendarEventsList = view.findViewById(R.id.calendarEventsList);
        calendarMonthYear = view.findViewById(R.id.calendarMonthYear);
        btnMonthPrev = view.findViewById(R.id.btnMonthPrev);
        btnMonthNext = view.findViewById(R.id.btnMonthNext);

        viewModel = new ViewModelProvider(this, new CalendarViewModelFactory()).get(CalendarViewModel.class);

        calendarView.setOnTouchListener((v, event) -> event.getAction() == MotionEvent.ACTION_MOVE);

        btnMonthPrev.setOnClickListener(v -> moveMonth(-1));
        btnMonthNext.setOnClickListener(v -> moveMonth(1));

        SharedGroupViewModel groupVm = new ViewModelProvider(requireActivity()).get(SharedGroupViewModel.class);
        groupVm.getGroupState().observe(getViewLifecycleOwner(), state -> {
            if (state != null && state.hasGroup()) {
                calendarNoGroupMessage.setVisibility(View.GONE);
                calendarScroll.setVisibility(View.VISIBLE);
                setupCalendar();
                YearMonth now = YearMonth.now();
                currentDisplayMonth = now;
                updateMonthYearTitle();
                viewModel.setCurrentMonth(now.getYear(), now.getMonthValue() - 1);
                viewModel.loadEventsForMonth(now.getYear(), now.getMonthValue() - 1);
            } else {
                calendarNoGroupMessage.setVisibility(View.VISIBLE);
                calendarScroll.setVisibility(View.GONE);
            }
        });

        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);
    }

    private void moveMonth(int delta) {
        if (currentDisplayMonth == null) return;
        currentDisplayMonth = currentDisplayMonth.plusMonths(delta);
        calendarView.smoothScrollToMonth(currentDisplayMonth);
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
        DayOfWeek firstDay = calFirst == java.util.Calendar.SUNDAY ? DayOfWeek.SUNDAY : DayOfWeek.of(calFirst - 1);
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
                boolean hasEvent = daysWithEvents.contains(key);
                boolean isMonthDate = day.getPosition() == DayPosition.MonthDate;
                container.eventDot.setVisibility(isMonthDate && hasEvent ? View.VISIBLE : View.GONE);
                if (!isMonthDate) {
                    container.dayText.setAlpha(0.3f);
                } else {
                    container.dayText.setAlpha(1f);
                }
            }
        });
        calendarView.setup(start, end, firstDay);
        calendarView.scrollToMonth(now);
    }

    private static String pad(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    private void onLoadResult(@Nullable Result<List<CalendarListItem>> result) {
        if (result == null) return;

        if (result instanceof Result.Success) {
            List<CalendarListItem> list = ((Result.Success<List<CalendarListItem>>) result).data;
            daysWithEvents.clear();
            for (CalendarListItem item : list) {
                if (item.date != null && item.date.length() >= 10) {
                    daysWithEvents.add(item.date.substring(0, 10));
                }
            }
            Integer year = viewModel.getCurrentYear().getValue();
            Integer month = viewModel.getCurrentMonth().getValue();
            if (year != null && month != null) {
                calendarView.notifyMonthChanged(YearMonth.of(year, month + 1));
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

    private void renderEventsList(List<CalendarListItem> events) {
        calendarEventsList.removeAllViews();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault());
        DateTimeFormatter dateTimeFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault());
        int paddingPx = getResources().getDimensionPixelSize(R.dimen.grid_2);
        int[] attrs = new int[]{android.R.attr.selectableItemBackground};
        TypedArray ta = requireContext().getTheme().obtainStyledAttributes(attrs);
        int rippleResId = ta.getResourceId(0, 0);
        ta.recycle();
        for (CalendarListItem item : events) {
            String line = formatEventLine(item, dateFmt, dateTimeFmt);
            TextView tv = new TextView(requireContext());
            tv.setText(line);
            tv.setTextAppearance(R.style.TextAppearance_CourseSync_Body);
            tv.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
            tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_right, 0);
            tv.setCompoundDrawablePadding(getResources().getDimensionPixelSize(R.dimen.grid_1));
            tv.setBackgroundResource(rippleResId);
            tv.setClickable(true);
            tv.setFocusable(true);
            UUID eventId = item.id;
            tv.setOnClickListener(v -> {
                if (eventId != null) {
                    Bundle args = new Bundle();
                    args.putString("eventId", eventId.toString());
                    NavHostFragment.findNavController(CalendarFragment.this)
                            .navigate(R.id.action_calendarFragment_to_calendarEventDetailFragment, args);
                }
            });
            calendarEventsList.addView(tv);
        }
    }

    private static String formatEventLine(CalendarListItem item, DateTimeFormatter dateFmt, DateTimeFormatter dateTimeFmt) {
        String name = item.name != null ? item.name : "";
        if (item.date == null || item.date.isEmpty()) {
            return name;
        }
        try {
            if (item.date.length() > 10 && !item.date.substring(11).startsWith("00:00")) {
                LocalDate date = LocalDate.parse(item.date.substring(0, 10));
                String timePart = item.date.length() >= 16 ? item.date.substring(11, 16) : "";
                return date.format(dateFmt) + (timePart.isEmpty() ? "" : " " + timePart) + " - " + name;
            } else {
                LocalDate date = LocalDate.parse(item.date.substring(0, 10));
                return date.format(dateFmt) + " - " + name;
            }
        } catch (DateTimeParseException e) {
            return name;
        }
    }
}
