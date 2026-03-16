package ru.katevpy.coursesync.shared.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

import ru.katevpy.coursesync.shared.dto.RegisterDeviceRequest;

public interface DeviceApi {

    @POST("device/register")
    Call<Void> register(@Body RegisterDeviceRequest request);
}
