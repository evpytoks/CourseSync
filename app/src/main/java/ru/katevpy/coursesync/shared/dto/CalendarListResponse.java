package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class CalendarListResponse {
    @SerializedName(value = "events", alternate = {"Events"})
    public List<CalendarListItem> events;
}
