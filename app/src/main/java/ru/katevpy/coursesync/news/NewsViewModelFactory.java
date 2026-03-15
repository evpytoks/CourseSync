package ru.katevpy.coursesync.news;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.shared.repository.NewsRepository;

public final class NewsViewModelFactory implements ViewModelProvider.Factory {

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        NewsRepository repo = new NewsRepository(App.getDeps().newsApi);
        return (T) new NewsViewModel(repo);
    }
}
