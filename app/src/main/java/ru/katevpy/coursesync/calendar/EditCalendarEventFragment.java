package ru.katevpy.coursesync.calendar;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import ru.katevpy.coursesync.ui.ErrorUi;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CalendarEventDetailsResponse;
import ru.katevpy.coursesync.shared.dto.CalendarEventTypeColorItem;
import ru.katevpy.coursesync.shared.dto.CourseListItem;
import ru.katevpy.coursesync.shared.util.Result;

public class EditCalendarEventFragment extends Fragment {

    private TextInputLayout eventNameLayout;
    private TextInputLayout eventGroupLayout;
    private TextInputLayout eventCourseLayout;
    private TextInputLayout eventTypeLayout;
    private TextInputLayout eventDateTimeLayout;
    private TextInputLayout eventDescriptionLayout;
    private TextInputEditText eventGroupDisplayInput;
    private MaterialAutoCompleteTextView eventCourseInput;
    private MaterialAutoCompleteTextView eventTypeInput;
    private TextInputEditText eventDateTimeInput;
    private EditCalendarEventViewModel viewModel;
    private LocalDateTime chosenDateTime;
    private UUID eventId;
    private boolean eventDetailLoaded;
    @Nullable
    private UUID pendingPreselectCourseId;
    @Nullable
    private String pendingPreselectEventType;
    @Nullable
    private UUID explicitCourseSelection;
    private String selectedEventType = "Другое";

    private final List<CourseListItem> courseItems = new ArrayList<>();
    private final List<CalendarEventTypeColorItem> eventTypeItems = new ArrayList<>();

    public EditCalendarEventFragment() {
        super(R.layout.fragment_edit_calendar_event);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        eventNameLayout = view.findViewById(R.id.eventNameLayout);
        eventGroupLayout = view.findViewById(R.id.eventGroupLayout);
        eventCourseLayout = view.findViewById(R.id.eventCourseLayout);
        eventTypeLayout = view.findViewById(R.id.eventTypeLayout);
        eventDateTimeLayout = view.findViewById(R.id.eventDateTimeLayout);
        eventDescriptionLayout = view.findViewById(R.id.eventDescriptionLayout);
        eventGroupDisplayInput = view.findViewById(R.id.eventGroupDisplayInput);
        eventCourseInput = view.findViewById(R.id.eventCourseInput);
        eventTypeInput = view.findViewById(R.id.eventTypeInput);
        eventDateTimeInput = view.findViewById(R.id.eventDateTimeInput);

        Bundle args = getArguments();
        if (args == null) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        String idStr = args.getString("eventId");
        if (idStr == null || idStr.isEmpty()) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        try {
            eventId = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        int maxName = getResources().getInteger(R.integer.max_event_name_length);
        int maxDesc = getResources().getInteger(R.integer.max_event_description_length);
        if (eventNameLayout.getEditText() != null) {
            eventNameLayout.getEditText().setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxName)});
        }
        if (eventDescriptionLayout.getEditText() != null) {
            eventDescriptionLayout.getEditText().setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxDesc)});
        }

        eventGroupDisplayInput.setEnabled(false);

        wireDropdownOpens(eventCourseInput);
        wireDropdownOpens(eventTypeInput);

        eventDateTimeInput.setOnClickListener(v -> openDateTimePicker());

        viewModel = new ViewModelProvider(this, new EditCalendarEventViewModelFactory()).get(EditCalendarEventViewModel.class);
        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);
        viewModel.getUpdateResult().observe(getViewLifecycleOwner(), this::onUpdateResult);
        viewModel.getCourses().observe(getViewLifecycleOwner(), this::onCourses);
        viewModel.getEventTypes().observe(getViewLifecycleOwner(), this::onEventTypes);

        view.findViewById(R.id.btnSaveEvent).setOnClickListener(v -> submit());

        viewModel.loadEvent(eventId);
    }

    private static void wireDropdownOpens(@Nullable MaterialAutoCompleteTextView actv) {
        if (actv == null) {
            return;
        }
        actv.setOnClickListener(v -> actv.showDropDown());
        actv.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                actv.showDropDown();
            }
        });
    }

    private void onCourses(@Nullable List<CourseListItem> list) {
        if (!eventDetailLoaded) {
            return;
        }
        courseItems.clear();
        if (list != null) {
            courseItems.addAll(list);
        }
        bindCourseDropdown();
    }

    private void onEventTypes(@Nullable List<CalendarEventTypeColorItem> list) {
        if (!eventDetailLoaded) {
            return;
        }
        eventTypeItems.clear();
        if (list != null) {
            eventTypeItems.addAll(list);
        }
        bindEventTypeDropdown();
    }

    private void bindCourseDropdown() {
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.calendar_event_no_course));
        for (CourseListItem c : courseItems) {
            labels.add(c.name != null ? c.name : "");
        }
        CalendarDropdownAdapter adapter = new CalendarDropdownAdapter(
                requireContext(), android.R.layout.simple_dropdown_item_1line, labels);
        eventCourseInput.setAdapter(adapter);

        UUID preselect = pendingPreselectCourseId;
        pendingPreselectCourseId = null;

        int selectIdx = 0;
        explicitCourseSelection = null;
        if (preselect != null) {
            for (int i = 0; i < courseItems.size(); i++) {
                if (preselect.equals(courseItems.get(i).id)) {
                    selectIdx = i + 1;
                    explicitCourseSelection = preselect;
                    break;
                }
            }
        }
        eventCourseInput.setText(labels.get(selectIdx), false);

        eventCourseInput.setOnItemClickListener((AdapterView<?> parent, View v, int position, long id) -> {
            if (position == 0) {
                explicitCourseSelection = null;
            } else {
                explicitCourseSelection = courseItems.get(position - 1).id;
            }
        });
    }

    private void bindEventTypeDropdown() {
        List<String> labels = new ArrayList<>();
        for (CalendarEventTypeColorItem item : eventTypeItems) {
            if (item.type != null && !item.type.trim().isEmpty()) {
                labels.add(item.type.trim());
            }
        }
        if (labels.isEmpty()) {
            labels.add("Другое");
        }

        CalendarDropdownAdapter adapter = new CalendarDropdownAdapter(
                requireContext(), android.R.layout.simple_dropdown_item_1line, labels);
        eventTypeInput.setAdapter(adapter);

        String preselect = pendingPreselectEventType;
        pendingPreselectEventType = null;

        int defIdx = 0;
        if (preselect != null && !preselect.isEmpty()) {
            for (int i = 0; i < labels.size(); i++) {
                if (preselect.equalsIgnoreCase(labels.get(i))) {
                    defIdx = i;
                    break;
                }
            }
        }
        selectedEventType = labels.get(defIdx);
        eventTypeInput.setText(selectedEventType, false);

        eventTypeInput.setOnItemClickListener((AdapterView<?> parent, View v, int position, long id) -> {
            selectedEventType = labels.get(position);
        });
    }

    private void fillForm(CalendarEventDetailsResponse data) {
        eventDetailLoaded = false;
        pendingPreselectCourseId = data.courseId;
        pendingPreselectEventType = data.eventType != null && !data.eventType.trim().isEmpty()
                ? data.eventType.trim()
                : "Другое";

        eventGroupDisplayInput.setText(data.groupName != null ? data.groupName : "");

        if (eventNameLayout.getEditText() != null) {
            eventNameLayout.getEditText().setText(data.name != null ? data.name : "");
        }
        if (eventDescriptionLayout.getEditText() != null) {
            eventDescriptionLayout.getEditText().setText(data.description != null ? data.description : "");
        }
        if (data.date != null && data.date.length() >= 10) {
            try {
                LocalDate date = LocalDate.parse(data.date.substring(0, 10));
                boolean hasTime = data.date.length() > 10 && !data.date.substring(11).startsWith("00:00");
                int hour = 0;
                int minute = 0;
                if (hasTime && data.date.length() >= 16) {
                    String timePart = data.date.substring(11, 16);
                    String[] parts = timePart.split(":");
                    if (parts.length >= 2) {
                        hour = Integer.parseInt(parts[0]);
                        minute = Integer.parseInt(parts[1]);
                    }
                }
                chosenDateTime = LocalDateTime.of(date, LocalTime.of(hour, minute));
                if (chosenDateTime.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                    eventDateTimeInput.setText(chosenDateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("ru"))));
                } else {
                    eventDateTimeInput.setText(chosenDateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", new Locale("ru"))));
                }
            } catch (DateTimeParseException | NumberFormatException ignored) {
            }
        }

        eventDetailLoaded = true;
        if (data.groupId != null) {
            viewModel.loadCoursesForGroup(data.groupId);
        } else {
            viewModel.loadCoursesForGroup(null);
        }
        viewModel.loadEventTypes();
    }

    private void setChosenAndDisplay(LocalDateTime dateTime) {
        chosenDateTime = dateTime;
        if (dateTime.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            eventDateTimeInput.setText(dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("ru"))));
        } else {
            eventDateTimeInput.setText(dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", new Locale("ru"))));
        }
        eventDateTimeLayout.setError(null);
    }

    private void openDateTimePicker() {
        LocalDate initial = chosenDateTime != null ? chosenDateTime.toLocalDate() : LocalDate.now();
        LocalTime initialTime = chosenDateTime != null ? chosenDateTime.toLocalTime() : LocalTime.of(12, 0);
        new DatePickerDialog(requireContext(), (picker, y, m, d) -> {
            LocalDate date = LocalDate.of(y, m + 1, d);
            new MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.event_ask_time)
                    .setPositiveButton(R.string.event_yes, (dialog, which) -> {
                        new TimePickerDialog(requireContext(), (timePicker, hour, minute) -> {
                            setChosenAndDisplay(LocalDateTime.of(date, LocalTime.of(hour, minute)));
                        }, initialTime.getHour(), initialTime.getMinute(), true).show();
                    })
                    .setNegativeButton(R.string.event_no, (dialog, which) ->
                            setChosenAndDisplay(LocalDateTime.of(date, LocalTime.MIDNIGHT)))
                    .show();
        }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
    }

    private void submit() {
        String name = eventNameLayout.getEditText() != null ? eventNameLayout.getEditText().getText().toString().trim() : "";
        String desc = eventDescriptionLayout.getEditText() != null ? eventDescriptionLayout.getEditText().getText().toString() : "";

        eventNameLayout.setError(null);
        eventCourseLayout.setError(null);
        eventTypeLayout.setError(null);
        eventDateTimeLayout.setError(null);
        eventDescriptionLayout.setError(null);

        int maxName = getResources().getInteger(R.integer.max_event_name_length);
        int maxDesc = getResources().getInteger(R.integer.max_event_description_length);
        if (name.length() > maxName) {
            eventNameLayout.setError(getString(R.string.event_name_max_length));
            return;
        }
        if (chosenDateTime == null) {
            eventDateTimeLayout.setError(getString(R.string.enter_event_date));
            return;
        }
        if (desc.length() > maxDesc) {
            eventDescriptionLayout.setError(getString(R.string.event_description_max_length));
            return;
        }

        String dateIso = chosenDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        viewModel.updateEvent(eventId, explicitCourseSelection, selectedEventType, name, dateIso, desc);
    }

    private void onLoadResult(@Nullable Result<CalendarEventDetailsResponse> result) {
        if (result == null) {
            return;
        }
        if (result instanceof Result.Success) {
            fillForm(((Result.Success<CalendarEventDetailsResponse>) result).data);
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<CalendarEventDetailsResponse>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
        NavHostFragment.findNavController(this).navigateUp();
    }

    private void onUpdateResult(@Nullable Result<Void> result) {
        if (result == null) {
            return;
        }
        if (result instanceof Result.Success) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<Void>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.update_event_server_error, ErrorUi.Duration.SHORT);
                return;
            }
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
    }
}
