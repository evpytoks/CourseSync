package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class UserSettingsResponse {
    @SerializedName("notifications_on")
    public boolean notificationsOn;

    @SerializedName("dark_theme_on")
    public boolean darkThemeOn;

    @SerializedName("calendar_event_type_colors")
    public List<CalendarEventTypeColorItem> calendarEventTypeColors;
}

