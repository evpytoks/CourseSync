package ru.katevpy.coursesync.shared.repository;

import com.google.gson.Gson;

import java.io.EOFException;
import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Response;

import ru.katevpy.coursesync.shared.dto.ApiError;
import ru.katevpy.coursesync.shared.dto.ErrorEnvelope;
import java.util.List;

import ru.katevpy.coursesync.shared.dto.UpdateCalendarEventTypeColorItem;
import ru.katevpy.coursesync.shared.dto.UpdateUserSettingsRequest;
import ru.katevpy.coursesync.shared.dto.UserSettingsResponse;
import ru.katevpy.coursesync.shared.network.SettingsApi;
import ru.katevpy.coursesync.shared.util.Result;

public class SettingsRepository {

    private final SettingsApi api;
    private final Gson gson = new Gson();

    public SettingsRepository(SettingsApi api) {
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

    public Result<UserSettingsResponse> getSettings() {
        try {
            Response<UserSettingsResponse> r = api.getSettings().execute();
            if (r.isSuccessful() && r.body() != null) {
                return Result.success(r.body());
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<Void> updateSettings(boolean notificationsOn, boolean darkThemeOn) {
        return updateSettings(notificationsOn, darkThemeOn, null);
    }

    public Result<Void> updateSettings(
            Boolean notificationsOn,
            Boolean darkThemeOn,
            List<UpdateCalendarEventTypeColorItem> calendarEventTypeColors) {
        try {
            UpdateUserSettingsRequest req = new UpdateUserSettingsRequest(
                    notificationsOn, darkThemeOn, calendarEventTypeColors);
            Response<Void> r = api.updateSettings(req).execute();
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

