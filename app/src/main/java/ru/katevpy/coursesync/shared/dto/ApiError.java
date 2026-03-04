package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class ApiError {
    @SerializedName(value = "code", alternate = {"Code"})
    public String code;
}
