package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class UserSettingsResponse {
    @SerializedName("notifications_on")
    public boolean notificationsOn;

    @SerializedName("dark_theme_on")
    public boolean darkThemeOn;
}

