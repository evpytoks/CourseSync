package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

public final class AddNewsRequest {
    @SerializedName("group_id")
    public String groupId;

    @SerializedName("text")
    public String text;

    public AddNewsRequest(UUID groupId, String text) {
        this.groupId = groupId.toString();
        this.text = text;
    }
}
