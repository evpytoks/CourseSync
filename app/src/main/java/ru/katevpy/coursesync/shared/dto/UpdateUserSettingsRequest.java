package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class UpdateUserSettingsRequest {
    @SerializedName("notifications_on")
    public Boolean notificationsOn;

    @SerializedName("dark_theme_on")
    public Boolean darkThemeOn;

    @SerializedName("calendar_event_type_colors")
    public List<UpdateCalendarEventTypeColorItem> calendarEventTypeColors;

    public UpdateUserSettingsRequest(Boolean notificationsOn, Boolean darkThemeOn) {
        this(notificationsOn, darkThemeOn, null);
    }

    public UpdateUserSettingsRequest(
            Boolean notificationsOn,
            Boolean darkThemeOn,
            List<UpdateCalendarEventTypeColorItem> calendarEventTypeColors) {
        this.notificationsOn = notificationsOn;
        this.darkThemeOn = darkThemeOn;
        this.calendarEventTypeColors = calendarEventTypeColors;
    }
}

