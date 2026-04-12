package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class GroupParticipantsResponse {
    @SerializedName(value = "participants", alternate = {"Participants"})
    public List<GroupParticipantItem> participants;
}
