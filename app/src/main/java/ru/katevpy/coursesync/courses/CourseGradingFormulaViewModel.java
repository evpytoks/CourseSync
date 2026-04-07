package ru.katevpy.coursesync.courses;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.shared.dto.CumulativeGradeState;
import ru.katevpy.coursesync.shared.dto.CourseGradingElementsResponse;
import ru.katevpy.coursesync.shared.dto.CourseGradingTextResponse;
import ru.katevpy.coursesync.shared.repository.CourseRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class CourseGradingFormulaViewModel extends ViewModel {

    private final CourseRepository repo = new CourseRepository(App.getDeps().courseApi);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<CourseGradingTextResponse>> gradingTextResult = new MutableLiveData<>();
    private final MutableLiveData<Result<CourseGradingElementsResponse>> gradingElementsResult = new MutableLiveData<>();
    private final MutableLiveData<Result<CumulativeGradeState>> cumulativeGradeResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Void>> saveCumulativeGradeResult = new MutableLiveData<>();
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

    public LiveData<Result<CumulativeGradeState>> getCumulativeGradeResult() {
        return cumulativeGradeResult;
    }

    public LiveData<Result<Void>> getSaveCumulativeGradeResult() {
        return saveCumulativeGradeResult;
    }

    public void loadGradingElements(UUID courseId) {
        io.execute(() -> {
            Result<CourseGradingElementsResponse> grading = repo.getGrading(courseId);
            gradingElementsResult.postValue(grading);
            if (grading instanceof Result.Success) {
                cumulativeGradeResult.postValue(repo.getCumulativeGrade(courseId));
            } else {
                cumulativeGradeResult.postValue(Result.success(new CumulativeGradeState(null, null)));
            }
        });
    }

    public void saveCumulativeGrade(
            UUID courseId,
            List<String> elementIds,
            Double blockGrade,
            Double automatic) {
        io.execute(() -> saveCumulativeGradeResult.postValue(
                repo.saveCumulativeGrade(courseId, elementIds, blockGrade, automatic)));
    }

    public void clearSaveCumulativeGradeResult() {
        saveCumulativeGradeResult.postValue(null);
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
