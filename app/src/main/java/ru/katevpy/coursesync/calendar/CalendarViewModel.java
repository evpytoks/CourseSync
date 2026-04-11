package ru.katevpy.coursesync.calendar;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final MutableLiveData<Result<Void>> toggleEventFailure = new MutableLiveData<>();
    private final List<CalendarListItem> monthEventsBuffer = new ArrayList<>();
    private final Set<UUID> togglingEventIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final AtomicInteger calendarLoadGeneration = new AtomicInteger(0);

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

    public LiveData<Result<Void>> getToggleEventFailure() {
        return toggleEventFailure;
    }

    public void consumeToggleEventFailure() {
        toggleEventFailure.setValue(null);
    }

    public void setCurrentMonth(int year, int month) {
        currentYear.setValue(year);
        currentMonth.setValue(month);
    }

    public void loadEventsForMonth(int year, int month) {
        LocalDate start = LocalDate.of(year, month + 1, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        loadEventsForRange(start, end);
    }

    public void loadEventsForRange(LocalDate start, LocalDate end) {
        LocalDate a = start;
        LocalDate b = end;
        if (b.isBefore(a)) {
            LocalDate t = a;
            a = b;
            b = t;
        }
        currentYear.postValue(a.getYear());
        currentMonth.postValue(a.getMonthValue() - 1);
        String startDate = formatLocalDate(a);
        String endDate = formatLocalDate(b);
        final int loadGen = calendarLoadGeneration.incrementAndGet();
        io.execute(() -> {
            Result<CalendarListResponse> res = repository.getEvents(startDate, endDate);
            if (loadGen != calendarLoadGeneration.get()) {
                return;
            }
            if (res instanceof Result.Success) {
                CalendarListResponse body = ((Result.Success<CalendarListResponse>) res).data;
                List<CalendarListItem> list = body != null && body.events != null ? body.events : Collections.emptyList();
                synchronized (monthEventsBuffer) {
                    monthEventsBuffer.clear();
                    monthEventsBuffer.addAll(list);
                    sortEventsForDisplay(monthEventsBuffer);
                    loadResult.postValue(Result.success(new ArrayList<>(monthEventsBuffer)));
                }
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

    private static String formatLocalDate(LocalDate d) {
        return formatDate(d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth());
    }

    public void toggleEventDone(@Nullable UUID eventId) {
        if (eventId == null || !togglingEventIds.add(eventId)) {
            return;
        }
        io.execute(() -> {
            try {
                Result<Void> r = repository.toggleEventDone(eventId);
                if (r instanceof Result.Success) {
                    synchronized (monthEventsBuffer) {
                        for (CalendarListItem it : monthEventsBuffer) {
                            if (eventId.equals(it.id)) {
                                it.isDone = !it.isDone;
                                break;
                            }
                        }
                        sortEventsForDisplay(monthEventsBuffer);
                        loadResult.postValue(Result.success(new ArrayList<>(monthEventsBuffer)));
                    }
                } else if (r instanceof Result.HttpError) {
                    Result.HttpError<?> he = (Result.HttpError<?>) r;
                    toggleEventFailure.postValue(Result.httpError(he.httpCode, he.error));
                } else if (r instanceof Result.NetworkError) {
                    Result.NetworkError<?> ne = (Result.NetworkError<?>) r;
                    toggleEventFailure.postValue(Result.networkError(ne.t));
                } else {
                    toggleEventFailure.postValue(Result.logicalError("Неизвестная ошибка"));
                }
            } finally {
                togglingEventIds.remove(eventId);
            }
        });
    }

    private static void sortEventsForDisplay(List<CalendarListItem> list) {
        list.sort(Comparator
                .comparing((CalendarListItem it) -> it.isDone)
                .thenComparing(it -> it.date != null ? it.date : "", Comparator.naturalOrder()));
    }

    private static String formatDate(int year, int month, int day) {
        String y = String.valueOf(year);
        String m = month < 9 ? "0" + (month + 1) : String.valueOf(month + 1);
        String d = day < 10 ? "0" + day : String.valueOf(day);
        return y + "-" + m + "-" + d;
    }
}
