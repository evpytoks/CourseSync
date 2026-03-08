package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class GroupJoinResponse {
    @SerializedName(value = "GroupId", alternate = {"groupId", "group_id"})
    public String groupId;

    @SerializedName(value = "Name", alternate = {"name"})
    public String name;

    @SerializedName(value = "Role", alternate = {"role"})
    public String role;
}
