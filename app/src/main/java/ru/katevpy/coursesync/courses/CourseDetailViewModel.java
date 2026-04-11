package ru.katevpy.coursesync.courses;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.dto.CalendarListItem;
import ru.katevpy.coursesync.shared.dto.CalendarListResponse;
import ru.katevpy.coursesync.shared.dto.CourseDetailsResponse;
import ru.katevpy.coursesync.shared.repository.CalendarRepository;
import ru.katevpy.coursesync.shared.repository.CourseRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class CourseDetailViewModel extends ViewModel {

    private final CourseRepository courseRepo;
    private final CalendarRepository calendarRepo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<CourseDetailsResponse>> loadResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Void>> deleteCourseResult = new MutableLiveData<>();
    private final MutableLiveData<Result<List<CalendarListItem>>> courseCalendarResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Void>> toggleCalendarFailure = new MutableLiveData<>();
    private final List<CalendarListItem> courseCalendarBuffer = new ArrayList<>();
    private final Set<UUID> togglingEventIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public CourseDetailViewModel(CourseRepository courseRepo, CalendarRepository calendarRepo) {
        this.courseRepo = courseRepo;
        this.calendarRepo = calendarRepo;
    }

    public LiveData<Result<CourseDetailsResponse>> getLoadResult() {
        return loadResult;
    }

    public LiveData<Result<Void>> getDeleteCourseResult() {
        return deleteCourseResult;
    }

    public LiveData<Result<List<CalendarListItem>>> getCourseCalendarResult() {
        return courseCalendarResult;
    }

    public LiveData<Result<Void>> getToggleCalendarFailure() {
        return toggleCalendarFailure;
    }

    public void consumeToggleCalendarFailure() {
        toggleCalendarFailure.setValue(null);
    }

    public void loadCourse(UUID id) {
        io.execute(() -> loadResult.postValue(courseRepo.getCourse(id)));
    }

    public void loadCourseCalendar(UUID courseId) {
        io.execute(() -> {
            Result<CalendarListResponse> res = courseRepo.getCourseCalendar(courseId);
            if (res instanceof Result.Success) {
                CalendarListResponse body = ((Result.Success<CalendarListResponse>) res).data;
                List<CalendarListItem> list = body != null && body.events != null
                        ? new ArrayList<>(body.events)
                        : new ArrayList<>();
                sortEventsForDisplay(list);
                synchronized (courseCalendarBuffer) {
                    courseCalendarBuffer.clear();
                    courseCalendarBuffer.addAll(list);
                    courseCalendarResult.postValue(Result.success(new ArrayList<>(courseCalendarBuffer)));
                }
            } else if (res instanceof Result.HttpError) {
                Result.HttpError<CalendarListResponse> he = (Result.HttpError<CalendarListResponse>) res;
                courseCalendarResult.postValue(Result.httpError(he.httpCode, he.error));
            } else if (res instanceof Result.NetworkError) {
                courseCalendarResult.postValue(Result.networkError(((Result.NetworkError<CalendarListResponse>) res).t));
            } else {
                courseCalendarResult.postValue(Result.logicalError("Неизвестная ошибка"));
            }
        });
    }

    public void toggleCourseCalendarEventDone(UUID eventId) {
        if (eventId == null || !togglingEventIds.add(eventId)) {
            return;
        }
        io.execute(() -> {
            try {
                Result<Void> r = calendarRepo.toggleEventDone(eventId);
                if (r instanceof Result.Success) {
                    synchronized (courseCalendarBuffer) {
                        for (CalendarListItem it : courseCalendarBuffer) {
                            if (eventId.equals(it.id)) {
                                it.isDone = !it.isDone;
                                break;
                            }
                        }
                        sortEventsForDisplay(courseCalendarBuffer);
                        courseCalendarResult.postValue(Result.success(new ArrayList<>(courseCalendarBuffer)));
                    }
                } else if (r instanceof Result.HttpError) {
                    Result.HttpError<?> he = (Result.HttpError<?>) r;
                    toggleCalendarFailure.postValue(Result.httpError(he.httpCode, he.error));
                } else if (r instanceof Result.NetworkError) {
                    Result.NetworkError<?> ne = (Result.NetworkError<?>) r;
                    toggleCalendarFailure.postValue(Result.networkError(ne.t));
                } else {
                    toggleCalendarFailure.postValue(Result.logicalError("Неизвестная ошибка"));
                }
            } finally {
                togglingEventIds.remove(eventId);
            }
        });
    }

    public void deleteCourse(UUID id) {
        deleteCourseResult.postValue(null);
        io.execute(() -> deleteCourseResult.postValue(courseRepo.deleteCourse(id)));
    }

    private static void sortEventsForDisplay(List<CalendarListItem> list) {
        list.sort(Comparator
                .comparing((CalendarListItem it) -> it.isDone)
                .thenComparing(it -> it.date != null ? it.date : "", Comparator.naturalOrder()));
    }
}
