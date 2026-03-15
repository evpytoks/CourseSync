package ru.katevpy.coursesync.calendar;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.repository.CalendarRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class CreateCalendarEventViewModel extends ViewModel {

    private final CalendarRepository repository;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<Void>> createResult = new MutableLiveData<>();

    public CreateCalendarEventViewModel(CalendarRepository repository) {
        this.repository = repository;
    }

    public LiveData<Result<Void>> getCreateResult() {
        return createResult;
    }

    public void createEvent(String name, String dateIso, String description) {
        io.execute(() -> {
            Result<Void> r = repository.addEvent(name, dateIso, description != null ? description : "");
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
