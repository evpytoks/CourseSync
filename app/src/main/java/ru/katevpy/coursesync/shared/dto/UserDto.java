package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class UserDto {
    @SerializedName("Id")
    public String id;

    @SerializedName("Email")
    public String email;
}
