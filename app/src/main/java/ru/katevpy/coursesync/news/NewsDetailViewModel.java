package ru.katevpy.coursesync.news;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.dto.NewsDetailsResponse;
import ru.katevpy.coursesync.shared.repository.NewsRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class NewsDetailViewModel extends ViewModel {

    private final NewsRepository repo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<NewsDetailsResponse>> loadResult = new MutableLiveData<>();

    public NewsDetailViewModel(NewsRepository repo) {
        this.repo = repo;
    }

    public LiveData<Result<NewsDetailsResponse>> getLoadResult() {
        return loadResult;
    }

    public void loadNews(UUID id) {
        io.execute(() -> {
            Result<NewsDetailsResponse> r = repo.getNews(id);
            if (r instanceof Result.Success) {
                loadResult.postValue(r);
            } else if (r instanceof Result.HttpError) {
                Result.HttpError<NewsDetailsResponse> he = (Result.HttpError<NewsDetailsResponse>) r;
                loadResult.postValue(Result.httpError(he.httpCode, he.error));
            } else if (r instanceof Result.NetworkError) {
                loadResult.postValue(Result.networkError(((Result.NetworkError<NewsDetailsResponse>) r).t));
            } else {
                loadResult.postValue(Result.logicalError("Неизвестная ошибка"));
            }
        });
    }
}
