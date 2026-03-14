package ru.katevpy.coursesync.settings;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.shared.repository.SettingsRepository;

public final class SettingsViewModelFactory implements ViewModelProvider.Factory {

    private final android.content.Context appContext;

    public SettingsViewModelFactory(android.content.Context appContext) {
        this.appContext = appContext;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        SettingsRepository repo = new SettingsRepository(App.getDeps().settingsApi);
        return (T) new SettingsViewModel(repo);
    }
}

