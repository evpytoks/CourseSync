package ru.katevpy.coursesync.calendar;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.dto.CalendarEventTypeColorItem;
import ru.katevpy.coursesync.shared.dto.CalendarEventTypeColorsResponse;
import ru.katevpy.coursesync.shared.dto.CourseListItem;
import ru.katevpy.coursesync.shared.dto.CourseListResponse;
import ru.katevpy.coursesync.shared.dto.OwnerGroupListItem;
import ru.katevpy.coursesync.shared.dto.OwnerGroupListResponse;
import ru.katevpy.coursesync.shared.repository.CalendarRepository;
import ru.katevpy.coursesync.shared.repository.CourseRepository;
import ru.katevpy.coursesync.shared.repository.GroupRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class CreateCalendarEventViewModel extends ViewModel {

    private final CalendarRepository calendarRepository;
    private final GroupRepository groupRepository;
    private final CourseRepository courseRepository;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<Void>> createResult = new MutableLiveData<>();
    private final MutableLiveData<List<OwnerGroupListItem>> ownerGroups = new MutableLiveData<>();
    private final MutableLiveData<Boolean> ownerGroupsLoadFailed = new MutableLiveData<>(false);
    private final MutableLiveData<List<CourseListItem>> courses = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<CalendarEventTypeColorItem>> eventTypes = new MutableLiveData<>(Collections.emptyList());

    public CreateCalendarEventViewModel(
            CalendarRepository calendarRepository,
            GroupRepository groupRepository,
            CourseRepository courseRepository
    ) {
        this.calendarRepository = calendarRepository;
        this.groupRepository = groupRepository;
        this.courseRepository = courseRepository;
    }

    public LiveData<Result<Void>> getCreateResult() {
        return createResult;
    }

    public LiveData<List<OwnerGroupListItem>> getOwnerGroups() {
        return ownerGroups;
    }

    public LiveData<Boolean> getOwnerGroupsLoadFailed() {
        return ownerGroupsLoadFailed;
    }

    public LiveData<List<CourseListItem>> getCourses() {
        return courses;
    }

    public LiveData<List<CalendarEventTypeColorItem>> getEventTypes() {
        return eventTypes;
    }

    public void loadOwnerGroupsAndEventTypes() {
        io.execute(() -> {
            Result<OwnerGroupListResponse> rg = groupRepository.getOwnerGroups();
            if (!(rg instanceof Result.Success)) {
                ownerGroupsLoadFailed.postValue(true);
                return;
            }
            ownerGroupsLoadFailed.postValue(false);
            OwnerGroupListResponse body = ((Result.Success<OwnerGroupListResponse>) rg).data;
            List<OwnerGroupListItem> og = Collections.emptyList();
            if (body != null && body.groups != null) {
                og = body.groups;
            }
            ownerGroups.postValue(og);

            List<CalendarEventTypeColorItem> et = Collections.emptyList();
            Result<CalendarEventTypeColorsResponse> rt = calendarRepository.getEventTypes();
            if (rt instanceof Result.Success) {
                CalendarEventTypeColorsResponse typesBody = ((Result.Success<CalendarEventTypeColorsResponse>) rt).data;
                if (typesBody != null && typesBody.items != null) {
                    et = typesBody.items;
                }
            }
            eventTypes.postValue(et);
        });
    }

    public void loadCoursesForGroup(@Nullable UUID groupId) {
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

    public void createEvent(UUID groupId, @Nullable UUID courseId, String eventType, String name, String dateIso, String description) {
        io.execute(() -> {
            Result<Void> r = calendarRepository.addEvent(
                    groupId,
                    courseId,
                    eventType,
                    name,
                    dateIso,
                    description != null ? description : "");
            if (r instanceof Result.Success) {
                createResult.postValue(Result.success(null));
            } else if (r instanceof Result.HttpError) {
                Result.HttpError<Void> he = (Result.HttpError<Void>) r;
                createResult.postValue(Result.httpError(he.httpCode, he.error));
            } else if (r instanceof Result.NetworkError) {
                createResult.postValue(Result.networkError(((Result.NetworkError<Void>) r).t));
            } else {
                createResult.postValue(Result.logicalError("Неизвестная ошибка"));
            }
        });
    }
}
