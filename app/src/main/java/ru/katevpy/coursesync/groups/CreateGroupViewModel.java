package ru.katevpy.coursesync.groups;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.dto.CreateGroupResponse;
import ru.katevpy.coursesync.shared.repository.GroupRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class CreateGroupViewModel extends ViewModel {

    private static final String NAME_PATTERN = "^[a-zA-Zа-яА-ЯёЁ0-9]{1,20}$";

    private final GroupRepository repo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Result<CreateGroupResponse>> createResult = new MutableLiveData<>();

    public CreateGroupViewModel(GroupRepository repo) {
        this.repo = repo;
    }

    public LiveData<Result<CreateGroupResponse>> getCreateResult() {
        return createResult;
    }

    public void createGroup(String name) {
        if (name == null) {
            createResult.postValue(Result.logicalError("Введите название"));
            return;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            createResult.postValue(Result.logicalError("Введите название"));
            return;
        }
        if (trimmed.length() > 20 || !trimmed.matches(NAME_PATTERN)) {
            createResult.postValue(Result.logicalError(
                    "длина от 1 до 20 только латинские и русские буквы и цифры"));
            return;
        }

        createResult.postValue(null);
        io.execute(() -> createResult.postValue(repo.createGroup(trimmed)));
    }
}
