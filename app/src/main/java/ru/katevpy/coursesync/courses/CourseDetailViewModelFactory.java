package ru.katevpy.coursesync.courses;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.shared.repository.CalendarRepository;
import ru.katevpy.coursesync.shared.repository.CourseRepository;

public final class CourseDetailViewModelFactory implements ViewModelProvider.Factory {

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        CourseRepository courseRepo = new CourseRepository(App.getDeps().courseApi);
        CalendarRepository calendarRepo = new CalendarRepository(App.getDeps().calendarApi);
        return (T) new CourseDetailViewModel(courseRepo, calendarRepo);
    }
}
