package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

public final class NewsListItem {
    public UUID id;
    @SerializedName("time")
    public String time;
    @SerializedName("group")
    public String group;
    @SerializedName("section")
    public String section;
    @SerializedName("text")
    public String text;
    @SerializedName(value = "is_read", alternate = {"IsRead", "isRead"})
    public boolean isRead;
}
