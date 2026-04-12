package ru.katevpy.coursesync.groups;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.dto.GroupParticipantItem;
import ru.katevpy.coursesync.shared.dto.GroupParticipantsResponse;
import ru.katevpy.coursesync.shared.repository.GroupRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class GroupMembersViewModel extends ViewModel {

    private final GroupRepository repository;
    private final UUID groupId;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<List<GroupParticipantItem>>> participantsResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Void>> toggleResult = new MutableLiveData<>();

    public GroupMembersViewModel(GroupRepository repository, UUID groupId) {
        this.repository = repository;
        this.groupId = groupId;
    }

    public LiveData<Result<List<GroupParticipantItem>>> getParticipantsResult() {
        return participantsResult;
    }

    public LiveData<Result<Void>> getToggleResult() {
        return toggleResult;
    }

    public void consumeToggleResult() {
        toggleResult.setValue(null);
    }

    public void loadParticipants() {
        io.execute(() -> {
            Result<GroupParticipantsResponse> res = repository.getGroupParticipants(groupId);
            if (res instanceof Result.Success) {
                GroupParticipantsResponse body = ((Result.Success<GroupParticipantsResponse>) res).data;
                List<GroupParticipantItem> list = body != null && body.participants != null
                        ? new ArrayList<>(body.participants)
                        : Collections.emptyList();
                participantsResult.postValue(Result.success(list));
            } else if (res instanceof Result.HttpError) {
                Result.HttpError<GroupParticipantsResponse> he = (Result.HttpError<GroupParticipantsResponse>) res;
                participantsResult.postValue(Result.httpError(he.httpCode, he.error));
            } else if (res instanceof Result.NetworkError) {
                participantsResult.postValue(Result.networkError(((Result.NetworkError<GroupParticipantsResponse>) res).t));
            } else {
                participantsResult.postValue(Result.logicalError("Ошибка"));
            }
        });
    }

    public void setBlocked(@NonNull String email, boolean currentlyBlocked) {
        io.execute(() -> {
            Result<Void> res = currentlyBlocked
                    ? repository.unblockGroupParticipant(groupId, email)
                    : repository.blockGroupParticipant(groupId, email);
            toggleResult.postValue(res);
        });
    }

    @Override
    protected void onCleared() {
        io.shutdown();
    }
}
