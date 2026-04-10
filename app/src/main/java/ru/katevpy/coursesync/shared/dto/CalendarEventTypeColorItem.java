package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class CalendarEventTypeColorItem {
    @SerializedName(value = "type", alternate = {"Type"})
    public String type;
    @SerializedName(value = "color", alternate = {"Color"})
    public String color;
}
