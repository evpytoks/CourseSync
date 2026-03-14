package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class UpdateUserSettingsRequest {
    @SerializedName("notifications_on")
    public Boolean notificationsOn;

    @SerializedName("dark_theme_on")
    public Boolean darkThemeOn;

    public UpdateUserSettingsRequest(Boolean notificationsOn, Boolean darkThemeOn) {
        this.notificationsOn = notificationsOn;
        this.darkThemeOn = darkThemeOn;
    }
}

