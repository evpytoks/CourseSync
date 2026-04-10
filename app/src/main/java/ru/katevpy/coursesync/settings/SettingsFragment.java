package ru.katevpy.coursesync.settings;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CalendarEventTypeColorItem;
import ru.katevpy.coursesync.shared.dto.UpdateCalendarEventTypeColorItem;
import ru.katevpy.coursesync.shared.dto.UserSettingsResponse;
import ru.katevpy.coursesync.shared.repository.AuthRepository;
import ru.katevpy.coursesync.shared.util.Result;
import ru.katevpy.coursesync.ui.ErrorUi;

public class SettingsFragment extends Fragment {

    private static final long COLOR_SAVE_DEBOUNCE_MS = 550L;

    private SettingsViewModel viewModel;
    private CheckBox notificationsCheckBox;
    private CheckBox darkThemeCheckBox;
    private LinearLayout settingsEventColorsContainer;
    private TextView calendarColorsSectionTitle;
    private final List<EventColorRow> eventColorRows = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable debouncedColorSave;
    private boolean suppressColorPersistence;
    private boolean isUpdating;
    private boolean isLoaded;
    private CompoundButton.OnCheckedChangeListener notificationsListener;
    private CompoundButton.OnCheckedChangeListener darkThemeListener;

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        notificationsCheckBox = view.findViewById(R.id.notificationsCheckBox);
        darkThemeCheckBox = view.findViewById(R.id.darkThemeCheckBox);
        Button btnLogout = view.findViewById(R.id.btnLogout);
        settingsEventColorsContainer = view.findViewById(R.id.settingsEventColorsContainer);
        calendarColorsSectionTitle = view.findViewById(R.id.calendarColorsSectionTitle);

        viewModel = new ViewModelProvider(
                this,
                new SettingsViewModelFactory(requireContext().getApplicationContext())
        ).get(SettingsViewModel.class);

        notificationsListener = (buttonView, isChecked) -> {
            if (isUpdating || !isLoaded) return;
            applySettingsChange();
        };
        darkThemeListener = (buttonView, isChecked) -> {
            if (isUpdating || !isLoaded) return;
            applySettingsChange();
        };
        bindCheckListeners();

        btnLogout.setOnClickListener(v -> performLogout());

        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);
        viewModel.getUpdateResult().observe(getViewLifecycleOwner(), this::onUpdateResult);

        viewModel.loadSettings();
    }

    private void applySettingsChange() {
        boolean notificationsOn = notificationsCheckBox.isChecked();
        boolean darkThemeOn = darkThemeCheckBox.isChecked();
        applyNightModeIfChanged(darkThemeOn);
        isUpdating = true;
        cancelDebouncedColorSave();
        setSettingsInputsEnabled(false);
        viewModel.updateSettings(notificationsOn, darkThemeOn);
    }

    private void applyNightModeIfChanged(boolean darkThemeOn) {
        int targetMode = darkThemeOn ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode);
        }
    }

    private void cancelDebouncedColorSave() {
        if (debouncedColorSave != null) {
            mainHandler.removeCallbacks(debouncedColorSave);
            debouncedColorSave = null;
        }
    }

    private void setSettingsInputsEnabled(boolean enabled) {
        notificationsCheckBox.setEnabled(enabled && isLoaded);
        darkThemeCheckBox.setEnabled(enabled && isLoaded);
        for (EventColorRow row : eventColorRows) {
            row.hex.setEnabled(enabled);
            row.preset.setEnabled(enabled);
        }
    }

    private void clearRowErrors() {
        for (EventColorRow row : eventColorRows) {
            row.hexLayout.setError(null);
        }
    }

    @Nullable
    private List<UpdateCalendarEventTypeColorItem> buildColorUpdatesOrShowErrors() {
        clearRowErrors();
        List<UpdateCalendarEventTypeColorItem> updates = new ArrayList<>();
        for (EventColorRow row : eventColorRows) {
            String normalized = ColorHex.normalizeOrNull(row.hex.getText().toString());
            if (normalized == null) {
                row.hexLayout.setError(getString(R.string.settings_calendar_colors_invalid));
                return null;
            }
            updates.add(new UpdateCalendarEventTypeColorItem(row.type, normalized));
        }
        return updates;
    }

    private void schedulePersistColorsFromUi() {
        if (suppressColorPersistence || !isLoaded || isUpdating) {
            return;
        }
        cancelDebouncedColorSave();
        debouncedColorSave = () -> {
            debouncedColorSave = null;
            persistColorsFromUiIfValid();
        };
        mainHandler.postDelayed(debouncedColorSave, COLOR_SAVE_DEBOUNCE_MS);
    }

    private void persistColorsFromUiIfValid() {
        if (suppressColorPersistence || !isLoaded || isUpdating) {
            return;
        }
        List<UpdateCalendarEventTypeColorItem> updates = buildColorUpdatesOrShowErrors();
        if (updates == null) {
            return;
        }
        isUpdating = true;
        setSettingsInputsEnabled(false);
        viewModel.updateCalendarColorsOnly(updates);
    }

    private void onLoadResult(@Nullable Result<UserSettingsResponse> result) {
        if (result == null) {
            return;
        }

        if (result instanceof Result.Success) {
            UserSettingsResponse data = ((Result.Success<UserSettingsResponse>) result).data;
            if (data != null) {
                unbindCheckListeners();
                notificationsCheckBox.setChecked(data.notificationsOn);
                darkThemeCheckBox.setChecked(data.darkThemeOn);
                bindCheckListeners();
                isLoaded = true;
                rebuildEventColorRows(data.calendarEventTypeColors);
            }
            isUpdating = false;
            setSettingsInputsEnabled(true);
            return;
        }

        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<UserSettingsResponse>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavOptions opts = new NavOptions.Builder()
                        .setPopUpTo(R.id.groupsFragment, true)
                        .build();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment, null, opts);
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.settings_load_error, ErrorUi.Duration.LONG);
                isUpdating = false;
                setSettingsInputsEnabled(true);
                return;
            }
        }

        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.LONG);
        isUpdating = false;
        setSettingsInputsEnabled(true);
    }

    private void rebuildEventColorRows(@Nullable List<CalendarEventTypeColorItem> items) {
        cancelDebouncedColorSave();
        eventColorRows.clear();
        settingsEventColorsContainer.removeAllViews();
        if (items == null || items.isEmpty()) {
            calendarColorsSectionTitle.setVisibility(View.GONE);
            settingsEventColorsContainer.setVisibility(View.GONE);
            return;
        }
        calendarColorsSectionTitle.setVisibility(View.VISIBLE);
        settingsEventColorsContainer.setVisibility(View.VISIBLE);

        suppressColorPersistence = true;
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (CalendarEventTypeColorItem item : items) {
            if (item == null || item.type == null || item.type.trim().isEmpty()) {
                continue;
            }
            String type = item.type.trim();
            View rowView = inflater.inflate(R.layout.item_settings_event_type_color, settingsEventColorsContainer, false);
            TextView title = rowView.findViewById(R.id.eventTypeTitle);
            View swatch = rowView.findViewById(R.id.colorSwatch);
            MaterialAutoCompleteTextView preset = rowView.findViewById(R.id.preset);
            TextInputLayout hexLayout = rowView.findViewById(R.id.hexLayout);
            TextInputEditText hex = rowView.findViewById(R.id.hex);

            title.setText(type);
            String apiColor = item.color != null && !item.color.trim().isEmpty()
                    ? item.color.trim()
                    : EventColorPresets.DEFAULT;
            hex.setText(apiColor);
            applySwatch(swatch, apiColor);

            EventColorRow row = new EventColorRow(type, swatch, hexLayout, hex, preset);
            bindPresetDropdown(row);
            hex.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    String n = ColorHex.normalizeOrNull(s != null ? s.toString() : "");
                    if (n != null) {
                        applySwatch(swatch, n);
                    } else {
                        applySwatchPlaceholder(swatch);
                    }
                    hexLayout.setError(null);
                    schedulePersistColorsFromUi();
                }
            });

            settingsEventColorsContainer.addView(rowView);
            eventColorRows.add(row);
        }

        settingsEventColorsContainer.post(() -> suppressColorPersistence = false);
    }

    private void bindPresetDropdown(EventColorRow row) {
        row.preset.setAdapter(new EventColorPresetAdapter(row.preset.getContext()));
        row.preset.setOnItemClickListener((parent, v, position, id) -> {
            String h = EventColorPresets.PRESETS[position];
            suppressColorPersistence = true;
            row.hex.setText(h);
            row.preset.setText(h, false);
            applySwatch(row.swatch, h);
            row.hexLayout.setError(null);
            suppressColorPersistence = false;
            persistColorsFromUiIfValid();
        });
    }

    private void applySwatch(@NonNull View swatch, @NonNull String hex) {
        int fill;
        try {
            fill = Color.parseColor(hex);
        } catch (IllegalArgumentException e) {
            fill = ContextCompat.getColor(swatch.getContext(), R.color.calendar_event_type_default);
        }
        applySwatchWithFill(swatch, fill);
    }

    private void applySwatchPlaceholder(@NonNull View swatch) {
        TypedValue tv = new TypedValue();
        swatch.getContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorSurfaceVariant, tv, true);
        applySwatchWithFill(swatch, tv.data);
    }

    private void applySwatchWithFill(@NonNull View swatch, int fillArgb) {
        TypedValue tv = new TypedValue();
        swatch.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutline, tv, true);
        int stroke = tv.data;
        int strokePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1f, swatch.getResources().getDisplayMetrics());
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fillArgb);
        d.setStroke(strokePx, stroke);
        swatch.setBackground(d);
    }

    private void onUpdateResult(@Nullable Result<Void> result) {
        if (result == null) {
            isUpdating = false;
            setSettingsInputsEnabled(true);
            return;
        }

        if (result instanceof Result.Success) {
            isUpdating = false;
            setSettingsInputsEnabled(true);
            return;
        }

        isUpdating = false;
        setSettingsInputsEnabled(true);
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<Void>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavOptions opts = new NavOptions.Builder()
                        .setPopUpTo(R.id.groupsFragment, true)
                        .build();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment, null, opts);
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.settings_save_error, ErrorUi.Duration.LONG);
                viewModel.loadSettings();
                return;
            }
        }

        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.LONG);
        viewModel.loadSettings();
    }

    private void performLogout() {
        AuthRepository authRepository = new AuthRepository(
                App.getDeps().authApi,
                App.getDeps().pendingLoginStorage,
                App.getDeps().tokenStorage
        );
        new Thread(() -> {
            Result<Void> result = authRepository.logout();
            requireActivity().runOnUiThread(() -> onLogoutResult(result));
        }).start();
    }

    private void onLogoutResult(Result<Void> result) {
        if (result instanceof Result.Success) {
            NavOptions opts = new NavOptions.Builder()
                    .setPopUpTo(R.id.groupsFragment, true)
                    .build();
            NavHostFragment.findNavController(this).navigate(R.id.loginFragment, null, opts);
            return;
        }

        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<Void>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavOptions opts = new NavOptions.Builder()
                        .setPopUpTo(R.id.groupsFragment, true)
                        .build();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment, null, opts);
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.logout_error, ErrorUi.Duration.LONG);
                return;
            }
        }

        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.LONG);
    }

    private void bindCheckListeners() {
        notificationsCheckBox.setOnCheckedChangeListener(notificationsListener);
        darkThemeCheckBox.setOnCheckedChangeListener(darkThemeListener);
    }

    private void unbindCheckListeners() {
        notificationsCheckBox.setOnCheckedChangeListener(null);
        darkThemeCheckBox.setOnCheckedChangeListener(null);
    }

    @Override
    public void onDestroyView() {
        cancelDebouncedColorSave();
        super.onDestroyView();
    }

    private static final class EventColorRow {
        final String type;
        final View swatch;
        final TextInputLayout hexLayout;
        final TextInputEditText hex;
        final MaterialAutoCompleteTextView preset;

        EventColorRow(
                String type,
                View swatch,
                TextInputLayout hexLayout,
                TextInputEditText hex,
                MaterialAutoCompleteTextView preset) {
            this.type = type;
            this.swatch = swatch;
            this.hexLayout = hexLayout;
            this.hex = hex;
            this.preset = preset;
        }
    }
}
