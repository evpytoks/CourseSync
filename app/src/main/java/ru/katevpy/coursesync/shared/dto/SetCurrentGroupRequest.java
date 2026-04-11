package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

public final class SetCurrentGroupRequest {

    @SerializedName("group_id")
    public UUID groupId;

    public SetCurrentGroupRequest(UUID groupId) {
        this.groupId = groupId;
    }
}
