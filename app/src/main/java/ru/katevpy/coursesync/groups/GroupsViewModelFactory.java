package ru.katevpy.coursesync.groups;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.shared.repository.GroupRepository;

public final class GroupsViewModelFactory implements ViewModelProvider.Factory {

    private final android.content.Context appContext;

    public GroupsViewModelFactory(android.content.Context appContext) {
        this.appContext = appContext;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        GroupRepository repo = new GroupRepository(App.getDeps().groupApi);
        return (T) new GroupsViewModel(repo);
    }
}