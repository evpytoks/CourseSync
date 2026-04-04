package ru.katevpy.coursesync.courses;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.dto.CourseDetailsResponse;
import ru.katevpy.coursesync.shared.repository.CourseRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class CourseDetailViewModel extends ViewModel {

    private final CourseRepository repo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<CourseDetailsResponse>> loadResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Void>> deleteCourseResult = new MutableLiveData<>();

    public CourseDetailViewModel(CourseRepository repo) {
        this.repo = repo;
    }

    public LiveData<Result<CourseDetailsResponse>> getLoadResult() {
        return loadResult;
    }

    public LiveData<Result<Void>> getDeleteCourseResult() {
        return deleteCourseResult;
    }

    public void loadCourse(UUID id) {
        io.execute(() -> loadResult.postValue(repo.getCourse(id)));
    }

    public void deleteCourse(UUID id) {
        deleteCourseResult.postValue(null);
        io.execute(() -> deleteCourseResult.postValue(repo.deleteCourse(id)));
    }
}
