package ru.katevpy.coursesync.courses;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.shared.dto.CourseGradingElementsResponse;
import ru.katevpy.coursesync.shared.dto.CourseGradingElementItem;
import ru.katevpy.coursesync.shared.dto.CourseGradingTextResponse;
import ru.katevpy.coursesync.shared.repository.CourseRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class EditCourseGradingFormulaViewModel extends ViewModel {

    private final CourseRepository repo = new CourseRepository(App.getDeps().courseApi);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<CourseGradingTextResponse>> loadResult = new MutableLiveData<>();
    private final MutableLiveData<Result<CourseGradingElementsResponse>> gradingElementsResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Void>> saveResult = new MutableLiveData<>();

    public LiveData<Result<CourseGradingTextResponse>> getLoadResult() {
        return loadResult;
    }

    public LiveData<Result<CourseGradingElementsResponse>> getGradingElementsResult() {
        return gradingElementsResult;
    }

    public LiveData<Result<Void>> getSaveResult() {
        return saveResult;
    }

    public void loadGradingText(UUID courseId) {
        io.execute(() -> loadResult.postValue(repo.getGradingText(courseId)));
    }

    public void loadGradingElements(UUID courseId) {
        io.execute(() -> gradingElementsResult.postValue(repo.getGrading(courseId)));
    }

    public void saveGrading(UUID courseId, String text, List<CourseGradingElementItem> elements) {
        io.execute(() -> saveResult.postValue(repo.saveGrading(courseId, text, elements)));
    }
}
