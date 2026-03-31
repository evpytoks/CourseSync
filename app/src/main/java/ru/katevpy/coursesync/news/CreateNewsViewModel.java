package ru.katevpy.coursesync.news;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.dto.OwnerGroupListResponse;
import ru.katevpy.coursesync.shared.repository.GroupRepository;
import ru.katevpy.coursesync.shared.repository.NewsRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class CreateNewsViewModel extends ViewModel {

    private final NewsRepository newsRepository;
    private final GroupRepository groupRepository;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<OwnerGroupListResponse>> ownerGroupsLoad = new MutableLiveData<>();
    private final MutableLiveData<Result<Void>> createResult = new MutableLiveData<>();

    public CreateNewsViewModel(NewsRepository newsRepository, GroupRepository groupRepository) {
        this.newsRepository = newsRepository;
        this.groupRepository = groupRepository;
    }

    public LiveData<Result<OwnerGroupListResponse>> getOwnerGroupsLoad() {
        return ownerGroupsLoad;
    }

    public LiveData<Result<Void>> getCreateResult() {
        return createResult;
    }

    public void loadOwnerGroups() {
        io.execute(() -> ownerGroupsLoad.postValue(groupRepository.getOwnerGroups()));
    }

    public void createNews(UUID groupId, String text) {
        io.execute(() -> {
            Result<Void> r = newsRepository.addNews(groupId, text);
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
