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
    private final MutableLiveData<Integer> unreadCount = new MutableLiveData<>(0);

    public NewsViewModel(NewsRepository repo) {
        this.repo = repo;
    }

    public LiveData<Result<List<NewsListItem>>> getLoadResult() {
        return loadResult;
    }

    public LiveData<Integer> getUnreadCount() {
        return unreadCount;
    }

    public void loadNews() {
        io.execute(() -> {
            Result<NewsListResponse> r = repo.getNewsList();
            if (r instanceof Result.Success) {
                NewsListResponse data = ((Result.Success<NewsListResponse>) r).data;
                List<NewsListItem> items = data != null && data.news != null ? data.news : Collections.emptyList();
                int u = 0;
                if (data != null && data.unreadCount != null && data.unreadCount > 0) {
                    u = data.unreadCount;
                }
                unreadCount.postValue(u);
                loadResult.postValue(Result.success(items));
            } else if (r instanceof Result.HttpError) {
                unreadCount.postValue(0);
                Result.HttpError<NewsListResponse> he = (Result.HttpError<NewsListResponse>) r;
                loadResult.postValue(Result.httpError(he.httpCode, he.error));
            } else if (r instanceof Result.NetworkError) {
                unreadCount.postValue(0);
                loadResult.postValue(Result.networkError(((Result.NetworkError<NewsListResponse>) r).t));
            } else {
                unreadCount.postValue(0);
                loadResult.postValue(Result.logicalError("Неизвестная ошибка"));
            }
        });
    }
}
