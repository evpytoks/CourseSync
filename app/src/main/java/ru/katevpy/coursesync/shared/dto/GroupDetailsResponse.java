package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class GroupDetailsResponse {
    @SerializedName(value = "Id", alternate = {"id"})
    public String id;

    @SerializedName(value = "Name", alternate = {"name"})
    public String name;

    @SerializedName(value = "Role", alternate = {"role"})
    public String role;

    @SerializedName(value = "GroupCode", alternate = {"group_code", "groupCode"})
    public String groupCode;
}
