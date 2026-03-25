package ru.katevpy.coursesync.courses;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.shared.repository.CourseRepository;

public final class CourseDetailViewModelFactory implements ViewModelProvider.Factory {

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        CourseRepository repo = new CourseRepository(App.getDeps().courseApi);
        return (T) new CourseDetailViewModel(repo);
    }
}
