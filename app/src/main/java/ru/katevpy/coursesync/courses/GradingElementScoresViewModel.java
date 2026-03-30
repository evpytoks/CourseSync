package ru.katevpy.coursesync.courses;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.shared.dto.CourseGradingScoresResponse;
import ru.katevpy.coursesync.shared.repository.CourseRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class GradingElementScoresViewModel extends ViewModel {

    private final CourseRepository repo = new CourseRepository(App.getDeps().courseApi);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<CourseGradingScoresResponse>> scoresResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Void>> saveResult = new MutableLiveData<>();

    public LiveData<Result<CourseGradingScoresResponse>> getScoresResult() {
        return scoresResult;
    }

    public LiveData<Result<Void>> getSaveResult() {
        return saveResult;
    }

    public void loadScores(UUID courseId, String elementName) {
        io.execute(() -> scoresResult.postValue(repo.getGradingScores(courseId, elementName)));
    }

    public void saveScores(UUID courseId, String elementName, List<Double> scores) {
        io.execute(() -> saveResult.postValue(repo.updateGradingScores(courseId, elementName, scores)));
    }

    public void clearSaveResult() {
        saveResult.postValue(null);
    }
}
