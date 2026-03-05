package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class ApiError {
    @SerializedName(value = "error_code", alternate = {"code", "Code", "errorCode"})
    public String code;
}
