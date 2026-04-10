package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class CalendarEventTypeColorsResponse {
    @SerializedName(value = "items", alternate = {"Items"})
    public List<CalendarEventTypeColorItem> items;
}
