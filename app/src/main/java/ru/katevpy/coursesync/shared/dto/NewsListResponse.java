package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class NewsListResponse {
    @SerializedName(value = "news", alternate = {"News"})
    public List<NewsListItem> news;
    @SerializedName(value = "unread_count", alternate = {"UnreadCount", "unreadCount"})
    public Integer unreadCount;
}
