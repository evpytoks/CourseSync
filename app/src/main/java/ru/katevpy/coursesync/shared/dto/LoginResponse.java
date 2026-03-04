package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class LoginResponse {
    @SerializedName(value = "token", alternate = {"Token"})
    public String token;

    @SerializedName(value = "refreshToken", alternate = {"RefreshToken"})
    public String refreshToken;
}
