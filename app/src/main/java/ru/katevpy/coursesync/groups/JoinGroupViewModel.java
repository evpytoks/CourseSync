package ru.katevpy.coursesync.groups;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.dto.GroupJoinResponse;
import ru.katevpy.coursesync.shared.repository.GroupRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class JoinGroupViewModel extends ViewModel {

    private final GroupRepository repo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Result<GroupJoinResponse>> joinResult = new MutableLiveData<>();

    public JoinGroupViewModel(GroupRepository repo) {
        this.repo = repo;
    }

    public LiveData<Result<GroupJoinResponse>> getJoinResult() {
        return joinResult;
    }

    public void joinGroup(String code) {
        if (code == null || code.trim().isEmpty()) {
            joinResult.postValue(Result.logicalError("Введите код"));
            return;
        }
        String trimmed = code.trim();
        joinResult.postValue(null);
        io.execute(() -> joinResult.postValue(repo.joinGroup(trimmed)));
    }
}
