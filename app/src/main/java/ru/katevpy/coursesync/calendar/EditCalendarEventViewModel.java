package ru.katevpy.coursesync.calendar;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.dto.CalendarEventDetailsResponse;
import ru.katevpy.coursesync.shared.dto.CalendarEventTypeColorItem;
import ru.katevpy.coursesync.shared.dto.CalendarEventTypeColorsResponse;
import ru.katevpy.coursesync.shared.dto.CourseListItem;
import ru.katevpy.coursesync.shared.dto.CourseListResponse;
import ru.katevpy.coursesync.shared.repository.CalendarRepository;
import ru.katevpy.coursesync.shared.repository.CourseRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class EditCalendarEventViewModel extends ViewModel {

    private final CalendarRepository calendarRepository;
    private final CourseRepository courseRepository;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<CalendarEventDetailsResponse>> loadResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Void>> updateResult = new MutableLiveData<>();
    private final MutableLiveData<List<CourseListItem>> courses = new MutableLiveData<>();
    private final MutableLiveData<List<CalendarEventTypeColorItem>> eventTypes = new MutableLiveData<>();

    public EditCalendarEventViewModel(CalendarRepository calendarRepository, CourseRepository courseRepository) {
        this.calendarRepository = calendarRepository;
        this.courseRepository = courseRepository;
    }

    public LiveData<Result<CalendarEventDetailsResponse>> getLoadResult() {
        return loadResult;
    }

    public LiveData<Result<Void>> getUpdateResult() {
        return updateResult;
    }

    public LiveData<List<CourseListItem>> getCourses() {
        return courses;
    }

    public LiveData<List<CalendarEventTypeColorItem>> getEventTypes() {
        return eventTypes;
    }

    public void loadEvent(UUID eventId) {
        io.execute(() -> {
            Result<CalendarEventDetailsResponse> r = calendarRepository.getEvent(eventId);
            if (r instanceof Result.Success) {
                loadResult.postValue(r);
            } else if (r instanceof Result.HttpError) {
                loadResult.postValue(r);
            } else if (r instanceof Result.NetworkError) {
                loadResult.postValue(r);
            } else {
                loadResult.postValue(Result.logicalError("Неизвестная ошибка"));
            }
        });
    }

    public void loadCoursesForGroup(UUID groupId) {
        if (groupId == null) {
            courses.postValue(Collections.emptyList());
            return;
        }
        io.execute(() -> {
            Result<CourseListResponse> r = courseRepository.getCourseListForGroup(groupId);
            if (r instanceof Result.Success) {
                CourseListResponse body = ((Result.Success<CourseListResponse>) r).data;
                if (body != null && body.courses != null) {
                    courses.postValue(body.courses);
                    return;
                }
            }
            courses.postValue(Collections.emptyList());
        });
    }

    public void loadEventTypes() {
        io.execute(() -> {
            List<CalendarEventTypeColorItem> et = Collections.emptyList();
            Result<CalendarEventTypeColorsResponse> rt = calendarRepository.getEventTypes();
            if (rt instanceof Result.Success) {
                CalendarEventTypeColorsResponse body = ((Result.Success<CalendarEventTypeColorsResponse>) rt).data;
                if (body != null && body.items != null) {
                    et = body.items;
                }
            }
            eventTypes.postValue(et);
        });
    }

    public void updateEvent(UUID eventId, UUID courseId, String eventType, String name, String dateIso, String description) {
        io.execute(() -> {
            Result<Void> r = calendarRepository.updateEvent(eventId, courseId, eventType, name, dateIso, description);
            if (r instanceof Result.Success) {
                updateResult.postValue(Result.success(null));
            } else if (r instanceof Result.HttpError) {
                updateResult.postValue(r);
            } else if (r instanceof Result.NetworkError) {
                updateResult.postValue(Result.networkError(((Result.NetworkError<Void>) r).t));
            } else {
                updateResult.postValue(Result.logicalError("Неизвестная ошибка"));
            }
        });
    }
}
