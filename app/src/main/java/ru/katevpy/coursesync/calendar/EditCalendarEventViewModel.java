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

public class EditCalendarEventViewModel extends ViewModel {

    private final CalendarRepository repository;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<CalendarEventDetailsResponse>> loadResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Void>> updateResult = new MutableLiveData<>();

    public EditCalendarEventViewModel(CalendarRepository repository) {
        this.repository = repository;
    }

    public LiveData<Result<CalendarEventDetailsResponse>> getLoadResult() {
        return loadResult;
    }

    public LiveData<Result<Void>> getUpdateResult() {
        return updateResult;
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

    public void updateEvent(UUID eventId, String name, String dateIso, String description) {
        io.execute(() -> {
            Result<Void> r = repository.updateEvent(eventId, name, dateIso, description);
            if (r instanceof Result.Success) {
                updateResult.postValue(Result.success(null));
            } else if (r instanceof Result.HttpError) {
                updateResult.postValue(r);
            } else if (r instanceof Result.NetworkError) {
                updateResult.postValue(Result.networkError(((Result.NetworkError<Void>) r).t));
            } else {
                updateResult.postValue(Result.logicalError("Неизвестная ошибка"));
            }
        });
    }
}
