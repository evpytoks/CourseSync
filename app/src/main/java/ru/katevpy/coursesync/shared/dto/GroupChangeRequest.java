package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class GroupChangeRequest {
    @SerializedName(value = "Name", alternate = {"name"})
    public String name;

    public GroupChangeRequest(String name) {
        this.name = name;
    }
}
