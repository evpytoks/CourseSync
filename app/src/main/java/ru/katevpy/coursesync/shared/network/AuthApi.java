package ru.katevpy.coursesync.shared.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import ru.katevpy.coursesync.shared.dto.SendCodeRequest;
import ru.katevpy.coursesync.shared.dto.SendCodeResponse;
import ru.katevpy.coursesync.shared.dto.LoginResponse;
import ru.katevpy.coursesync.shared.dto.LoginRequest;
import ru.katevpy.coursesync.shared.dto.RefreshRequest;
import ru.katevpy.coursesync.shared.dto.RefreshResponse;

public interface AuthApi {
    @POST("/auth/send-code")
    Call<SendCodeResponse> sendCode(@Body SendCodeRequest body);

    @POST("/auth/login")
    Call<LoginResponse> login(@Body LoginRequest body);

    @POST("/auth/refresh")
    Call<RefreshResponse> refresh(@Body RefreshRequest body);

    @POST("/auth/logout")
    Call<Void> logout(@Body RefreshRequest body);
}
