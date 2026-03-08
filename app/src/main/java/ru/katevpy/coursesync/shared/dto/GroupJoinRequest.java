package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class GroupJoinRequest {
    @SerializedName(value = "Code", alternate = {"code"})
    public String code;

    public GroupJoinRequest(String code) {
        this.code = code;
    }
}
