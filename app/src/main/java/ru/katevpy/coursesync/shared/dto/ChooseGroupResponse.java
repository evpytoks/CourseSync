package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class ChooseGroupResponse {
    @SerializedName(value = "Id", alternate = {"id"})
    public String id;

    @SerializedName(value = "Name", alternate = {"name"})
    public String name;
}
