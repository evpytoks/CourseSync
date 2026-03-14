package ru.katevpy.coursesync.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.UserSettingsResponse;
import ru.katevpy.coursesync.shared.repository.AuthRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class SettingsFragment extends Fragment {

    private SettingsViewModel viewModel;
    private CheckBox notificationsCheckBox;
    private CheckBox darkThemeCheckBox;
    private boolean isUpdating;

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        notificationsCheckBox = view.findViewById(R.id.notificationsCheckBox);
        darkThemeCheckBox = view.findViewById(R.id.darkThemeCheckBox);
        Button btnLogout = view.findViewById(R.id.btnLogout);

        viewModel = new ViewModelProvider(
                this,
                new SettingsViewModelFactory(requireContext().getApplicationContext())
        ).get(SettingsViewModel.class);

        notificationsCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdating) return;
            applySettingsChange();
        });
        darkThemeCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdating) return;
            applySettingsChange();
        });

        btnLogout.setOnClickListener(v -> performLogout());

        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);
        viewModel.getUpdateResult().observe(getViewLifecycleOwner(), this::onUpdateResult);

        viewModel.loadSettings();
    }

    private void applySettingsChange() {
        boolean notificationsOn = notificationsCheckBox.isChecked();
        boolean darkThemeOn = darkThemeCheckBox.isChecked();
        isUpdating = true;
        viewModel.updateSettings(notificationsOn, darkThemeOn);
    }

    private void onLoadResult(@Nullable Result<UserSettingsResponse> result) {
        if (result == null) {
            return;
        }

        if (result instanceof Result.Success) {
            UserSettingsResponse data = ((Result.Success<UserSettingsResponse>) result).data;
            if (data != null) {
                isUpdating = true;
                notificationsCheckBox.setChecked(data.notificationsOn);
                darkThemeCheckBox.setChecked(data.darkThemeOn);
                isUpdating = false;
                AppCompatDelegate.setDefaultNightMode(
                        data.darkThemeOn ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
                );
            }
            return;
        }

        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<UserSettingsResponse>) result).httpCode;
            if (code == 401) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                App.getDeps().tokenStorage.clear();
                NavOptions opts = new NavOptions.Builder()
                        .setPopUpTo(R.id.groupsFragment, true)
                        .build();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment, null, opts);
                return;
            }
            if (code == 500) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                Snackbar.make(requireView(), R.string.settings_load_error, Snackbar.LENGTH_LONG).show();
                return;
            }
        }

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_LONG).show();
    }

    private void onUpdateResult(@Nullable Result<Void> result) {
        isUpdating = false;
        if (result == null) {
            return;
        }

        if (result instanceof Result.Success) {
            viewModel.loadSettings();
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
                Snackbar.make(requireView(), R.string.settings_save_error, Snackbar.LENGTH_LONG).show();
                return;
            }
        }

        Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_LONG).show();
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
                Snackbar.make(requireView(), R.string.logout_error, Snackbar.LENGTH_LONG).show();
                return;
            }
        }

        Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_LONG).show();
    }
}
