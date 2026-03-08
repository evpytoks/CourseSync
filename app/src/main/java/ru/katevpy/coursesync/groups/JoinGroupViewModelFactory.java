package ru.katevpy.coursesync.groups;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.shared.repository.GroupRepository;

public final class JoinGroupViewModelFactory implements ViewModelProvider.Factory {

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        GroupRepository repo = new GroupRepository(App.getDeps().groupApi);
        return (T) new JoinGroupViewModel(repo);
    }
}
