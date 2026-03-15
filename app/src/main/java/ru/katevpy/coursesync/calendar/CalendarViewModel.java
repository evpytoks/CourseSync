package ru.katevpy.coursesync.calendar;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.shared.dto.CalendarListItem;
import ru.katevpy.coursesync.shared.dto.CalendarListResponse;
import ru.katevpy.coursesync.shared.repository.CalendarRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class CalendarViewModel extends ViewModel {

    private final CalendarRepository repository;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<List<CalendarListItem>>> loadResult = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentYear = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentMonth = new MutableLiveData<>();

    public CalendarViewModel(CalendarRepository repository) {
        this.repository = repository;
        Calendar c = Calendar.getInstance();
        currentYear.setValue(c.get(Calendar.YEAR));
        currentMonth.setValue(c.get(Calendar.MONTH));
    }

    public LiveData<Result<List<CalendarListItem>>> getLoadResult() {
        return loadResult;
    }

    public LiveData<Integer> getCurrentYear() {
        return currentYear;
    }

    public LiveData<Integer> getCurrentMonth() {
        return currentMonth;
    }

    public void setCurrentMonth(int year, int month) {
        currentYear.setValue(year);
        currentMonth.setValue(month);
    }

    public void loadEventsForMonth(int year, int month) {
        int lastDay = getLastDayOfMonth(year, month);
        String startDate = formatDate(year, month, 1);
        String endDate = formatDate(year, month, lastDay);
        io.execute(() -> {
            Result<CalendarListResponse> res = repository.getEvents(startDate, endDate);
            if (res instanceof Result.Success) {
                CalendarListResponse body = ((Result.Success<CalendarListResponse>) res).data;
                List<CalendarListItem> list = body != null && body.events != null ? body.events : Collections.emptyList();
                loadResult.postValue(Result.success(list));
            } else if (res instanceof Result.HttpError) {
                Result.HttpError<CalendarListResponse> he = (Result.HttpError<CalendarListResponse>) res;
                loadResult.postValue(Result.httpError(he.httpCode, he.error));
            } else if (res instanceof Result.NetworkError) {
                loadResult.postValue(Result.networkError(((Result.NetworkError<CalendarListResponse>) res).t));
            } else {
                loadResult.postValue(Result.logicalError("Неизвестная ошибка"));
            }
        });
    }

    private static int getLastDayOfMonth(int year, int month) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private static String formatDate(int year, int month, int day) {
        String y = String.valueOf(year);
        String m = month < 9 ? "0" + (month + 1) : String.valueOf(month + 1);
        String d = day < 10 ? "0" + day : String.valueOf(day);
        return y + "-" + m + "-" + d;
    }
}
