package ru.katevpy.coursesync.calendar;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import ru.katevpy.coursesync.ui.ErrorUi;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.util.Result;

public class CreateCalendarEventFragment extends Fragment {

    private TextInputLayout eventNameLayout;
    private TextInputLayout eventDateTimeLayout;
    private TextInputLayout eventDescriptionLayout;
    private TextInputEditText eventDateTimeInput;
    private CreateCalendarEventViewModel viewModel;
    private LocalDateTime chosenDateTime;

    public CreateCalendarEventFragment() {
        super(R.layout.fragment_create_calendar_event);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        eventNameLayout = view.findViewById(R.id.eventNameLayout);
        eventDateTimeLayout = view.findViewById(R.id.eventDateTimeLayout);
        eventDescriptionLayout = view.findViewById(R.id.eventDescriptionLayout);
        eventDateTimeInput = view.findViewById(R.id.eventDateTimeInput);

        int maxName = getResources().getInteger(R.integer.max_event_name_length);
        int maxDesc = getResources().getInteger(R.integer.max_event_description_length);
        if (eventNameLayout.getEditText() != null) {
            eventNameLayout.getEditText().setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxName)});
        }
        if (eventDescriptionLayout.getEditText() != null) {
            eventDescriptionLayout.getEditText().setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxDesc)});
        }

        eventDateTimeInput.setOnClickListener(v -> openDateTimePicker());

        viewModel = new ViewModelProvider(this, new CreateCalendarEventViewModelFactory()).get(CreateCalendarEventViewModel.class);

        view.findViewById(R.id.btnCreateEvent).setOnClickListener(v -> submit());

        viewModel.getCreateResult().observe(getViewLifecycleOwner(), this::onCreateResult);
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
        eventDateTimeLayout.setError(null);
        eventDescriptionLayout.setError(null);

        if (name.isEmpty()) {
            eventNameLayout.setError(getString(R.string.enter_event_name));
            return;
        }
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
        viewModel.createEvent(name, dateIso, desc);
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
