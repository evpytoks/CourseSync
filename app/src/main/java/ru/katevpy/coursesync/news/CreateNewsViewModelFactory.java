package ru.katevpy.coursesync.news;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.shared.repository.GroupRepository;
import ru.katevpy.coursesync.shared.repository.NewsRepository;

public final class CreateNewsViewModelFactory implements ViewModelProvider.Factory {

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        NewsRepository newsRepo = new NewsRepository(App.getDeps().newsApi);
        GroupRepository groupRepo = new GroupRepository(App.getDeps().groupApi);
        return (T) new CreateNewsViewModel(newsRepo, groupRepo);
    }
}
