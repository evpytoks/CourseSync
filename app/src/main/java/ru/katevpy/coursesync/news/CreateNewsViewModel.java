package ru.katevpy.coursesync.news;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.repository.NewsRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class CreateNewsViewModel extends ViewModel {

    private final NewsRepository repository;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<Void>> createResult = new MutableLiveData<>();

    public CreateNewsViewModel(NewsRepository repository) {
        this.repository = repository;
    }

    public LiveData<Result<Void>> getCreateResult() {
        return createResult;
    }

    public void createNews(String name, String description) {
        io.execute(() -> {
            Result<Void> r = repository.addNews(name, description != null ? description : "");
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
