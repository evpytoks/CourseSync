package ru.katevpy.coursesync.groups;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.dto.GroupListItem;
import ru.katevpy.coursesync.shared.dto.GroupListResponse;
import ru.katevpy.coursesync.shared.repository.GroupRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class GroupsViewModel extends ViewModel {

    private final GroupRepository repo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Result<List<GroupListItem>>> groupsResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Void>> deleteGroupResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Void>> leaveGroupResult = new MutableLiveData<>();

    public GroupsViewModel(GroupRepository repo) {
        this.repo = repo;
    }

    public LiveData<Result<List<GroupListItem>>> getGroupsResult() {
        return groupsResult;
    }

    public LiveData<Result<Void>> getDeleteGroupResult() {
        return deleteGroupResult;
    }

    public void deleteGroup(UUID groupId) {
        deleteGroupResult.postValue(null);
        io.execute(() -> deleteGroupResult.postValue(repo.deleteGroup(groupId)));
    }

    public LiveData<Result<Void>> getLeaveGroupResult() {
        return leaveGroupResult;
    }

    public void leaveGroup(UUID groupId) {
        leaveGroupResult.postValue(null);
        io.execute(() -> leaveGroupResult.postValue(repo.leaveGroup(groupId)));
    }

    public void loadGroups() {
        io.execute(() -> {
            Result<GroupListResponse> r = repo.getGroups();

            if (r instanceof Result.Success) {
                List<GroupListItem> items = ((Result.Success<GroupListResponse>) r).data.items;
                groupsResult.postValue(Result.success(items != null ? items : Collections.emptyList()));
            } else if (r instanceof Result.HttpError) {
                groupsResult.postValue(Result.httpError(((Result.HttpError<GroupListResponse>) r).httpCode, ((Result.HttpError<GroupListResponse>) r).error));
            } else if (r instanceof Result.NetworkError) {
                groupsResult.postValue(Result.networkError(((Result.NetworkError<GroupListResponse>) r).t));
            } else if (r instanceof Result.LogicalError) {
                groupsResult.postValue(Result.logicalError(((Result.LogicalError<GroupListResponse>) r).message));
            } else {
                groupsResult.postValue(Result.logicalError("Неизвестная ошибка"));
            }
        });
    }
}
