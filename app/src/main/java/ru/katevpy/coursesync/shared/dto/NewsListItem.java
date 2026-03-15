package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

public final class NewsListItem {
    public UUID id;
    public String name;
    @SerializedName("created_at")
    public String createdAt;
}
