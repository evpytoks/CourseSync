package ru.katevpy.coursesync.shared.repository;

import com.google.gson.Gson;

import java.io.EOFException;
import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Response;

import java.util.UUID;

import ru.katevpy.coursesync.shared.dto.AddCalendarEventRequest;
import ru.katevpy.coursesync.shared.dto.ApiError;
import ru.katevpy.coursesync.shared.dto.CalendarEventDetailsResponse;
import ru.katevpy.coursesync.shared.dto.CalendarListResponse;
import ru.katevpy.coursesync.shared.dto.UpdateCalendarEventRequest;
import ru.katevpy.coursesync.shared.dto.ErrorEnvelope;
import ru.katevpy.coursesync.shared.network.CalendarApi;
import ru.katevpy.coursesync.shared.util.Result;

public class CalendarRepository {

    private final CalendarApi api;
    private final Gson gson = new Gson();

    public CalendarRepository(CalendarApi api) {
        this.api = api;
    }

    private ApiError parseError(ResponseBody body) {
        if (body == null) return null;
        try {
            ErrorEnvelope env = gson.fromJson(body.string(), ErrorEnvelope.class);
            return env != null ? env.error : null;
        } catch (Exception e) {
            return null;
        }
    }

    public Result<CalendarListResponse> getEvents(String startDate, String endDate) {
        try {
            Response<CalendarListResponse> r = api.listEvents(startDate, endDate).execute();
            if (r.isSuccessful() && r.body() != null) {
                return Result.success(r.body());
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<Void> addEvent(String name, String dateIso, String description) {
        try {
            AddCalendarEventRequest req = new AddCalendarEventRequest(name, dateIso, description);
            Response<Void> r = api.addEvent(req).execute();
            if (r.isSuccessful()) {
                return Result.success(null);
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            if (e instanceof EOFException) {
                return Result.success(null);
            }
            return Result.networkError(e);
        }
    }

    public Result<CalendarEventDetailsResponse> getEvent(UUID id) {
        try {
            Response<CalendarEventDetailsResponse> r = api.getEvent(id).execute();
            if (r.isSuccessful() && r.body() != null) {
                return Result.success(r.body());
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<Void> updateEvent(UUID id, String name, String dateIso, String description) {
        try {
            UpdateCalendarEventRequest req = new UpdateCalendarEventRequest(name, dateIso, description);
            Response<Void> r = api.changeEvent(id, req).execute();
            if (r.isSuccessful()) {
                return Result.success(null);
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            if (e instanceof EOFException) {
                return Result.success(null);
            }
            return Result.networkError(e);
        }
    }

    public Result<Void> deleteEvent(UUID id) {
        try {
            Response<Void> r = api.deleteEvent(id).execute();
            if (r.isSuccessful()) {
                return Result.success(null);
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            if (e instanceof EOFException) {
                return Result.success(null);
            }
            return Result.networkError(e);
        }
    }
}
