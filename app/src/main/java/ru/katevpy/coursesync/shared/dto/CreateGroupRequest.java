package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class CreateGroupRequest {
    @SerializedName(value = "Name", alternate = {"name"})
    public String name;

    public CreateGroupRequest(String name) {
        this.name = name;
    }
}
