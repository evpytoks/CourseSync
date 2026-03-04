package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class RefreshResponse {
    @SerializedName("Token")
    public String token;

    @SerializedName("RefreshToken")
    public String refreshToken;
}
