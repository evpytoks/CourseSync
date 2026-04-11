package ru.katevpy.coursesync.shared.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.POST;

import ru.katevpy.coursesync.shared.dto.RegisterDeviceRequest;
import ru.katevpy.coursesync.shared.dto.UnregisterDeviceRequest;

public interface DeviceApi {

    @POST("devices")
    Call<Void> register(@Body RegisterDeviceRequest request);

    @DELETE("devices")
    Call<Void> unregister(@Body UnregisterDeviceRequest request);
}
