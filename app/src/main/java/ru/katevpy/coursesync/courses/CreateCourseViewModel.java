package ru.katevpy.coursesync.courses;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.dto.CourseContactPersonItem;
import ru.katevpy.coursesync.shared.dto.CourseUsefulLinkItem;
import ru.katevpy.coursesync.shared.repository.CourseRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class CreateCourseViewModel extends ViewModel {

    private final CourseRepository repo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Result<Void>> createResult = new MutableLiveData<>();

    public CreateCourseViewModel(CourseRepository repo) {
        this.repo = repo;
    }

    public LiveData<Result<Void>> getCreateResult() {
        return createResult;
    }

    public void createCourse(
            String name,
            String generalInfo,
            List<CourseContactPersonItem> contacts,
            List<CourseUsefulLinkItem> usefulLinks) {
        createResult.postValue(null);
        io.execute(() -> createResult.postValue(repo.createCourse(name, generalInfo, contacts, usefulLinks)));
    }
}
