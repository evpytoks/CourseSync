package ru.katevpy.coursesync.news;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.dto.NewsListItem;
import ru.katevpy.coursesync.shared.dto.NewsListResponse;
import ru.katevpy.coursesync.shared.repository.NewsRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class NewsViewModel extends ViewModel {

    private final NewsRepository repo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<List<NewsListItem>>> loadResult = new MutableLiveData<>();

    public NewsViewModel(NewsRepository repo) {
        this.repo = repo;
    }

    public LiveData<Result<List<NewsListItem>>> getLoadResult() {
        return loadResult;
    }

    public void loadNews() {
        io.execute(() -> {
            Result<NewsListResponse> r = repo.getNewsList();
            if (r instanceof Result.Success) {
                List<NewsListItem> items = ((Result.Success<NewsListResponse>) r).data.news;
                loadResult.postValue(Result.success(items != null ? items : Collections.emptyList()));
            } else if (r instanceof Result.HttpError) {
                Result.HttpError<NewsListResponse> he = (Result.HttpError<NewsListResponse>) r;
                loadResult.postValue(Result.httpError(he.httpCode, he.error));
            } else if (r instanceof Result.NetworkError) {
                loadResult.postValue(Result.networkError(((Result.NetworkError<NewsListResponse>) r).t));
            } else {
                loadResult.postValue(Result.logicalError("Неизвестная ошибка"));
            }
        });
    }
}
