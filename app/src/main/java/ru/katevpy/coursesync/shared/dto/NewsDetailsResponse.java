package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

public final class NewsDetailsResponse {
    public UUID id;
    public String name;
    public String description;
    @SerializedName("created_at")
    public String createdAt;
}
