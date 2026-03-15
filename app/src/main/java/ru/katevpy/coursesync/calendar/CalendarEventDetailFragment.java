package ru.katevpy.coursesync.calendar;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CalendarEventDetailsResponse;
import ru.katevpy.coursesync.shared.util.Result;

public class CalendarEventDetailFragment extends Fragment {

    private TextView eventDetailDate;
    private TextView eventDetailName;
    private TextView eventDetailDescription;
    private UUID eventId;

    public CalendarEventDetailFragment() {
        super(R.layout.fragment_calendar_event_detail);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        eventDetailDate = view.findViewById(R.id.eventDetailDate);
        eventDetailName = view.findViewById(R.id.eventDetailName);
        eventDetailDescription = view.findViewById(R.id.eventDetailDescription);

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

        CalendarEventDetailViewModel viewModel = new ViewModelProvider(
                this,
                new CalendarEventDetailViewModelFactory()
        ).get(CalendarEventDetailViewModel.class);

        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (eventId != null) {
            CalendarEventDetailViewModel vm = new ViewModelProvider(this, new CalendarEventDetailViewModelFactory()).get(CalendarEventDetailViewModel.class);
            vm.loadEvent(eventId);
        }
    }

    private void onLoadResult(@Nullable Result<CalendarEventDetailsResponse> result) {
        if (result == null) return;

        if (result instanceof Result.Success) {
            CalendarEventDetailsResponse data = ((Result.Success<CalendarEventDetailsResponse>) result).data;
            eventDetailDate.setText(formatDate(data.date));
            eventDetailName.setText(data.name != null ? data.name : "");
            eventDetailDescription.setText(data.description != null ? data.description : "");
            eventDetailDescription.setVisibility(
                    data.description != null && !data.description.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }

        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<CalendarEventDetailsResponse>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            if (code == 404 || code == 403) {
                Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }
            if (code == 500) {
                Snackbar.make(requireView(), R.string.calendar_load_error, Snackbar.LENGTH_SHORT).show();
                return;
            }
        }
        Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_SHORT).show();
    }

    private static String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "";
        try {
            if (dateStr.length() >= 10) {
                LocalDate date = LocalDate.parse(dateStr.substring(0, 10));
                String formatted = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("ru")));
                if (dateStr.length() > 10 && !dateStr.substring(11).startsWith("00:00")) {
                    String timePart = dateStr.length() >= 16 ? dateStr.substring(11, 16) : dateStr.substring(11);
                    return formatted + " " + timePart;
                }
                return formatted;
            }
        } catch (DateTimeParseException ignored) {
        }
        return dateStr;
    }
}
