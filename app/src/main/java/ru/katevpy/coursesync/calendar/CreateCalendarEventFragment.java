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

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import ru.katevpy.coursesync.ui.ErrorUi;
import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.GroupState;
import ru.katevpy.coursesync.shared.SharedGroupViewModel;
import ru.katevpy.coursesync.shared.dto.CalendarEventTypeColorItem;
import ru.katevpy.coursesync.shared.dto.CourseListItem;
import ru.katevpy.coursesync.shared.dto.OwnerGroupListItem;
import ru.katevpy.coursesync.shared.util.Result;

public class CreateCalendarEventFragment extends Fragment {

    private TextInputLayout eventNameLayout;
    private TextInputLayout eventGroupLayout;
    private TextInputLayout eventDateTimeLayout;
    private TextInputLayout eventDescriptionLayout;
    private TextInputEditText eventNameInput;
    private MaterialAutoCompleteTextView eventGroupInput;
    private MaterialAutoCompleteTextView eventCourseInput;
    private MaterialAutoCompleteTextView eventTypeInput;
    private TextInputEditText eventDateTimeInput;
    private CreateCalendarEventViewModel viewModel;
    private LocalDateTime chosenDateTime;

    private final List<OwnerGroupListItem> ownerGroupItems = new ArrayList<>();
    private final List<CourseListItem> courseItems = new ArrayList<>();
    private final List<CalendarEventTypeColorItem> eventTypeItems = new ArrayList<>();

    @Nullable
    private UUID explicitGroupSelection;
    @Nullable
    private UUID explicitCourseSelection;
    private String selectedEventType = "Другое";

    public CreateCalendarEventFragment() {
        super(R.layout.fragment_create_calendar_event);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        eventNameLayout = view.findViewById(R.id.eventNameLayout);
        eventGroupLayout = view.findViewById(R.id.eventGroupLayout);
        eventDateTimeLayout = view.findViewById(R.id.eventDateTimeLayout);
        eventDescriptionLayout = view.findViewById(R.id.eventDescriptionLayout);
        eventNameInput = view.findViewById(R.id.eventNameInput);
        eventGroupInput = view.findViewById(R.id.eventGroupInput);
        eventCourseInput = view.findViewById(R.id.eventCourseInput);
        eventTypeInput = view.findViewById(R.id.eventTypeInput);
        eventDateTimeInput = view.findViewById(R.id.eventDateTimeInput);

        int maxName = getResources().getInteger(R.integer.max_event_name_length);
        int maxDesc = getResources().getInteger(R.integer.max_event_description_length);
        if (eventNameInput != null) {
            eventNameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxName)});
        }
        if (eventDescriptionLayout.getEditText() != null) {
            eventDescriptionLayout.getEditText().setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxDesc)});
        }

        wireDropdownOpens(eventGroupInput);
        wireDropdownOpens(eventCourseInput);
        wireDropdownOpens(eventTypeInput);

        eventDateTimeInput.setOnClickListener(v -> openDateTimePicker());

        viewModel = new ViewModelProvider(this, new CreateCalendarEventViewModelFactory()).get(CreateCalendarEventViewModel.class);

        view.findViewById(R.id.btnCreateEvent).setOnClickListener(v -> submit());

        viewModel.getCreateResult().observe(getViewLifecycleOwner(), this::onCreateResult);
        viewModel.getOwnerGroupsLoadFailed().observe(getViewLifecycleOwner(), failed -> {
            if (!Boolean.TRUE.equals(failed)) {
                return;
            }
            ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
            NavHostFragment.findNavController(this).navigateUp();
        });
        viewModel.getOwnerGroups().observe(getViewLifecycleOwner(), list -> {
            if (Boolean.TRUE.equals(viewModel.getOwnerGroupsLoadFailed().getValue())) {
                return;
            }
            if (list == null) {
                return;
            }
            onOwnerGroups(list);
        });
        viewModel.getCourses().observe(getViewLifecycleOwner(), this::onCourses);
        viewModel.getEventTypes().observe(getViewLifecycleOwner(), this::onEventTypes);

        viewModel.loadOwnerGroupsAndEventTypes();
    }

    private static void wireDropdownOpens(@Nullable MaterialAutoCompleteTextView actv) {
        if (actv == null) return;
        actv.setOnClickListener(v -> actv.showDropDown());
        actv.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                actv.showDropDown();
            }
        });
    }

    private void onOwnerGroups(List<OwnerGroupListItem> list) {
        ownerGroupItems.clear();
        if (list != null) {
            ownerGroupItems.addAll(list);
        }
        bindGroupDropdown();
    }

    private void onCourses(List<CourseListItem> list) {
        courseItems.clear();
        if (list != null) {
            courseItems.addAll(list);
        }
        bindCourseDropdown();
    }

    private void onEventTypes(List<CalendarEventTypeColorItem> list) {
        eventTypeItems.clear();
        if (list != null) {
            eventTypeItems.addAll(list);
        }
        bindEventTypeDropdown();
    }

    private void bindGroupDropdown() {
        eventGroupLayout.setError(null);
        if (ownerGroupItems.isEmpty()) {
            ErrorUi.show(this, R.string.calendar_create_need_owner_group, ErrorUi.Duration.SHORT);
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        List<String> labels = new ArrayList<>();
        for (OwnerGroupListItem g : ownerGroupItems) {
            labels.add(g.name != null ? g.name : "");
        }
        CalendarDropdownAdapter adapter = new CalendarDropdownAdapter(
                requireContext(), android.R.layout.simple_dropdown_item_1line, labels);
        eventGroupInput.setAdapter(adapter);

        int defaultIdx = 0;
        SharedGroupViewModel groupVm = new ViewModelProvider(requireActivity()).get(SharedGroupViewModel.class);
        GroupState gs = groupVm.getGroupState().getValue();
        String currentGid = gs != null ? gs.groupId : null;
        if (currentGid != null) {
            for (int i = 0; i < ownerGroupItems.size(); i++) {
                String id = ownerGroupItems.get(i).id;
                if (id != null && currentGid.trim().equalsIgnoreCase(id.trim())) {
                    defaultIdx = i;
                    break;
                }
            }
        }

        try {
            explicitGroupSelection = UUID.fromString(ownerGroupItems.get(defaultIdx).id.trim());
        } catch (Exception e) {
            explicitGroupSelection = null;
            ErrorUi.show(this, R.string.calendar_create_need_owner_group, ErrorUi.Duration.SHORT);
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        eventGroupInput.setText(labels.get(defaultIdx), false);
        viewModel.loadCoursesForGroup(explicitGroupSelection);

        eventGroupInput.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            OwnerGroupListItem g = ownerGroupItems.get(position);
            try {
                explicitGroupSelection = UUID.fromString(g.id.trim());
                eventGroupLayout.setError(null);
                viewModel.loadCoursesForGroup(explicitGroupSelection);
            } catch (IllegalArgumentException e) {
                explicitGroupSelection = null;
            }
        });
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
        explicitCourseSelection = null;
        eventCourseInput.setText(labels.get(0), false);

        eventCourseInput.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
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

        int defIdx = 0;
        for (int i = 0; i < labels.size(); i++) {
            if ("Другое".equalsIgnoreCase(labels.get(i))) {
                defIdx = i;
                break;
            }
        }
        selectedEventType = labels.get(defIdx);
        eventTypeInput.setText(selectedEventType, false);

        eventTypeInput.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            selectedEventType = labels.get(position);
        });
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
        String name = eventNameInput != null ? eventNameInput.getText().toString().trim() : "";
        String desc = eventDescriptionLayout.getEditText() != null ? eventDescriptionLayout.getEditText().getText().toString() : "";

        eventNameLayout.setError(null);
        eventGroupLayout.setError(null);
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

        UUID groupId = explicitGroupSelection;
        if (groupId == null) {
            eventGroupLayout.setError(getString(R.string.calendar_event_group_required));
            return;
        }

        String dateIso = chosenDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        viewModel.createEvent(groupId, explicitCourseSelection, selectedEventType, name, dateIso, desc);
    }

    private void onCreateResult(@Nullable Result<Void> result) {
        if (result == null) return;

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
                ErrorUi.show(this, R.string.create_event_server_error, ErrorUi.Duration.SHORT);
                return;
            }
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
    }
}
