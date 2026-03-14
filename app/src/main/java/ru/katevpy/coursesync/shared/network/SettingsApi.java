package ru.katevpy.coursesync.shared.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;

import ru.katevpy.coursesync.shared.dto.UpdateUserSettingsRequest;
import ru.katevpy.coursesync.shared.dto.UserSettingsResponse;

public interface SettingsApi {

    @GET("settings")
    Call<UserSettingsResponse> getSettings();

    @PUT("settings")
    Call<Void> updateSettings(@Body UpdateUserSettingsRequest request);
}

