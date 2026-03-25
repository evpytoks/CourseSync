package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

public final class CourseMaterialListItem {
    public UUID id;
    public String name;
    @SerializedName("author_email")
    public String authorEmail;
    @SerializedName("created_at")
    public String createdAt;
}
