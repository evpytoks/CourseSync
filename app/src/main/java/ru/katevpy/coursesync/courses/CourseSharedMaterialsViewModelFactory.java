package ru.katevpy.coursesync.courses;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public final class CourseSharedMaterialsViewModelFactory implements ViewModelProvider.Factory {

    private final Application application;

    public CourseSharedMaterialsViewModelFactory(@NonNull Application application) {
        this.application = application;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(CourseSharedMaterialsViewModel.class)) {
            return (T) new CourseSharedMaterialsViewModel(application);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
