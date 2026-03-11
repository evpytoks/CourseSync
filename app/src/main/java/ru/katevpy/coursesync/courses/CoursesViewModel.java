package ru.katevpy.coursesync.courses;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.dto.CourseListItem;
import ru.katevpy.coursesync.shared.dto.CourseListResponse;
import ru.katevpy.coursesync.shared.repository.CourseRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class CoursesViewModel extends ViewModel {

    private final CourseRepository repo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Result<List<CourseListItem>>> loadResult = new MutableLiveData<>();

    public CoursesViewModel(CourseRepository repo) {
        this.repo = repo;
    }

    public LiveData<Result<List<CourseListItem>>> getLoadResult() {
        return loadResult;
    }

    public void loadCourses() {
        io.execute(() -> {
            Result<CourseListResponse> r = repo.getCourseList();
            if (r instanceof Result.Success) {
                List<CourseListItem> items = ((Result.Success<CourseListResponse>) r).data.courses;
                loadResult.postValue(Result.success(items != null ? items : Collections.emptyList()));
            } else if (r instanceof Result.HttpError) {
                Result.HttpError<CourseListResponse> he = (Result.HttpError<CourseListResponse>) r;
                loadResult.postValue(Result.httpError(he.httpCode, he.error));
            } else if (r instanceof Result.NetworkError) {
                loadResult.postValue(Result.networkError(((Result.NetworkError<CourseListResponse>) r).t));
            } else {
                loadResult.postValue(Result.logicalError("Неизвестная ошибка"));
            }
        });
    }
}
