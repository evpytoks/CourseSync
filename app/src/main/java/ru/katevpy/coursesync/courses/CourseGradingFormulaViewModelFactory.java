package ru.katevpy.coursesync.courses;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public final class CourseGradingFormulaViewModelFactory implements ViewModelProvider.Factory {

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(CourseGradingFormulaViewModel.class)) {
            return (T) new CourseGradingFormulaViewModel();
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
