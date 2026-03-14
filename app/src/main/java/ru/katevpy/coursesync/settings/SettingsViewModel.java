package ru.katevpy.coursesync.settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.dto.UserSettingsResponse;
import ru.katevpy.coursesync.shared.repository.SettingsRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class SettingsViewModel extends ViewModel {

    private final SettingsRepository repo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Result<UserSettingsResponse>> loadResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Void>> updateResult = new MutableLiveData<>();

    public SettingsViewModel(SettingsRepository repo) {
        this.repo = repo;
    }

    public LiveData<Result<UserSettingsResponse>> getLoadResult() {
        return loadResult;
    }

    public LiveData<Result<Void>> getUpdateResult() {
        return updateResult;
    }

    public void loadSettings() {
        io.execute(() -> loadResult.postValue(repo.getSettings()));
    }

    public void updateSettings(boolean notificationsOn, boolean darkThemeOn) {
        io.execute(() -> updateResult.postValue(repo.updateSettings(notificationsOn, darkThemeOn)));
    }
}

