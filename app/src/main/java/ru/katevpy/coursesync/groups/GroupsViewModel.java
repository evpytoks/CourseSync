package ru.katevpy.coursesync.groups;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.dto.GroupListItem;
import ru.katevpy.coursesync.shared.dto.GroupListResponse;
import ru.katevpy.coursesync.shared.repository.GroupRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class GroupsViewModel extends ViewModel {

    private final GroupRepository repo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<List<GroupListItem>> groups = new MutableLiveData<>();

    public GroupsViewModel(GroupRepository repo) {
        this.repo = repo;
    }

    public LiveData<List<GroupListItem>> getGroups() {
        return groups;
    }

    public void loadGroups() {
        io.execute(() -> {
            Result<GroupListResponse> r = repo.getGroups();

            if (r instanceof Result.Success) {
                groups.postValue(((Result.Success<GroupListResponse>) r).data.items);
            } else {
                groups.postValue(null);
            }
        });
    }
}