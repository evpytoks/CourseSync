package ru.katevpy.coursesync.courses;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public final class EditCourseGradingFormulaViewModelFactory implements ViewModelProvider.Factory {

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(EditCourseGradingFormulaViewModel.class)) {
            return (T) new EditCourseGradingFormulaViewModel();
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
