package ru.katevpy.coursesync.calendar;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.shared.repository.CalendarRepository;
import ru.katevpy.coursesync.shared.repository.CourseRepository;
import ru.katevpy.coursesync.shared.repository.GroupRepository;

public final class CreateCalendarEventViewModelFactory implements ViewModelProvider.Factory {

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        CalendarRepository calendarRepo = new CalendarRepository(App.getDeps().calendarApi);
        GroupRepository groupRepo = new GroupRepository(App.getDeps().groupApi);
        CourseRepository courseRepo = new CourseRepository(App.getDeps().courseApi);
        return (T) new CreateCalendarEventViewModel(calendarRepo, groupRepo, courseRepo);
    }
}
