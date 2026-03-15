package ru.katevpy.coursesync.calendar;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.dto.CalendarEventDetailsResponse;
import ru.katevpy.coursesync.shared.repository.CalendarRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class CalendarEventDetailViewModel extends ViewModel {

    private final CalendarRepository repository;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<CalendarEventDetailsResponse>> loadResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Void>> deleteResult = new MutableLiveData<>();

    public CalendarEventDetailViewModel(CalendarRepository repository) {
        this.repository = repository;
    }

    public LiveData<Result<CalendarEventDetailsResponse>> getLoadResult() {
        return loadResult;
    }

    public LiveData<Result<Void>> getDeleteResult() {
        return deleteResult;
    }

    public void loadEvent(UUID eventId) {
        io.execute(() -> {
            Result<CalendarEventDetailsResponse> r = repository.getEvent(eventId);
            if (r instanceof Result.Success) {
                loadResult.postValue(r);
            } else if (r instanceof Result.HttpError) {
                loadResult.postValue(r);
            } else if (r instanceof Result.NetworkError) {
                loadResult.postValue(r);
            } else {
                loadResult.postValue(Result.logicalError("Неизвестная ошибка"));
            }
        });
    }

    public void deleteEvent(UUID eventId) {
        io.execute(() -> {
            Result<Void> r = repository.deleteEvent(eventId);
            if (r instanceof Result.Success) {
                deleteResult.postValue(Result.success(null));
            } else if (r instanceof Result.HttpError) {
                deleteResult.postValue(r);
            } else if (r instanceof Result.NetworkError) {
                deleteResult.postValue(Result.networkError(((Result.NetworkError<Void>) r).t));
            } else {
                deleteResult.postValue(Result.logicalError("Неизвестная ошибка"));
            }
        });
    }
}
