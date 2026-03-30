package ru.katevpy.coursesync.courses;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public final class GradingElementScoresViewModelFactory implements ViewModelProvider.Factory {

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(GradingElementScoresViewModel.class)) {
            return (T) new GradingElementScoresViewModel();
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
