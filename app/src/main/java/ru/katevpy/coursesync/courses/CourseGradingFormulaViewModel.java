package ru.katevpy.coursesync.courses;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.shared.dto.CourseGradingElementsResponse;
import ru.katevpy.coursesync.shared.dto.CourseGradingTextResponse;
import ru.katevpy.coursesync.shared.repository.CourseRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class CourseGradingFormulaViewModel extends ViewModel {

    private final CourseRepository repo = new CourseRepository(App.getDeps().courseApi);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<CourseGradingTextResponse>> gradingTextResult = new MutableLiveData<>();
    private final MutableLiveData<Result<CourseGradingElementsResponse>> gradingElementsResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadInProgress = new MutableLiveData<>(false);

    public LiveData<Result<CourseGradingTextResponse>> getGradingTextResult() {
        return gradingTextResult;
    }

    public LiveData<Boolean> getLoadInProgress() {
        return loadInProgress;
    }

    public LiveData<Result<CourseGradingElementsResponse>> getGradingElementsResult() {
        return gradingElementsResult;
    }

    public void loadGradingElements(UUID courseId) {
        io.execute(() -> gradingElementsResult.postValue(repo.getGrading(courseId)));
    }

    public void loadGradingText(UUID courseId) {
        io.execute(() -> {
            loadInProgress.postValue(true);
            try {
                gradingTextResult.postValue(repo.getGradingText(courseId));
            } finally {
                loadInProgress.postValue(false);
            }
        });
    }

    public void clearGradingTextResult() {
        gradingTextResult.postValue(null);
    }
}
