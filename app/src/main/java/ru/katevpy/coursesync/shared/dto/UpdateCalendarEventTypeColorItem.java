package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class UpdateCalendarEventTypeColorItem {

    @SerializedName("type")
    public String type;

    @SerializedName("color")
    public String color;

    public UpdateCalendarEventTypeColorItem(String type, String color) {
        this.type = type;
        this.color = color;
    }
}
