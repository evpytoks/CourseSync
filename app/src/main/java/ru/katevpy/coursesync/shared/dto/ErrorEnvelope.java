package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class ErrorEnvelope {
    @SerializedName(value = "error", alternate = {"Error"})
    public ApiError error;
}