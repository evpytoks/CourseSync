package ru.katevpy.coursesync.courses;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.dto.CourseDetailsResponse;
import ru.katevpy.coursesync.shared.dto.CourseUsefulLinkItem;
import ru.katevpy.coursesync.shared.repository.CourseRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class EditCourseViewModel extends ViewModel {

    private final CourseRepository repo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<CourseDetailsResponse>> loadResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Void>> updateResult = new MutableLiveData<>();

    public EditCourseViewModel(CourseRepository repo) {
        this.repo = repo;
    }

    public LiveData<Result<CourseDetailsResponse>> getLoadResult() {
        return loadResult;
    }

    public LiveData<Result<Void>> getUpdateResult() {
        return updateResult;
    }

    public void loadCourse(UUID id) {
        io.execute(() -> loadResult.postValue(repo.getCourse(id)));
    }

    public void updateCourse(UUID id, String name, String generalInfo, List<CourseUsefulLinkItem> usefulLinks) {
        io.execute(() -> updateResult.postValue(repo.updateCourse(id, name, generalInfo, usefulLinks)));
    }
}
