package ru.katevpy.coursesync.groups;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.shared.repository.GroupRepository;

public final class GroupMembersViewModelFactory implements ViewModelProvider.Factory {

    private final GroupRepository repository;
    private final UUID groupId;

    public GroupMembersViewModelFactory(Context appContext, UUID groupId) {
        this.repository = new GroupRepository(App.getDeps().groupApi);
        this.groupId = groupId;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(GroupMembersViewModel.class)) {
            return (T) new GroupMembersViewModel(repository, groupId);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
