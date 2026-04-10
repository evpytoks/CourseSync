package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class NewsUnreadCountResponse {
    @SerializedName(value = "unread_count", alternate = {"UnreadCount", "unreadCount"})
    public Integer unreadCount;
}
