package ru.katevpy.coursesync.calendar;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.repository.CalendarRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class EventDetailToolbarViewModel extends ViewModel {

    private final CalendarRepository repository;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<Void>> deleteResult = new MutableLiveData<>();

    public EventDetailToolbarViewModel(CalendarRepository repository) {
        this.repository = repository;
    }

    public LiveData<Result<Void>> getDeleteResult() {
        return deleteResult;
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
