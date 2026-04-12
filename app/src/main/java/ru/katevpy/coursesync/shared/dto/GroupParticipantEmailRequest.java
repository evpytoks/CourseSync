package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class GroupParticipantEmailRequest {
    @SerializedName(value = "email", alternate = {"Email"})
    public String email;

    public GroupParticipantEmailRequest(String email) {
        this.email = email;
    }
}
