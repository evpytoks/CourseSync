package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class CreateGroupResponse {
    @SerializedName(value = "Id", alternate = {"id"})
    public String id;

    @SerializedName(value = "Name", alternate = {"name"})
    public String name;

    @SerializedName(value = "Code", alternate = {"code"})
    public String code;
}
