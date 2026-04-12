package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class GroupParticipantItem {
    @SerializedName(value = "email", alternate = {"Email"})
    public String email;
    @SerializedName(value = "is_blocked", alternate = {"IsBlocked"})
    public boolean isBlocked;
}
