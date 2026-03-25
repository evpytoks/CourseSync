package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

public final class CourseDetailsResponse {
    public UUID id;
    public String name;
    @SerializedName("general_info")
    public String generalInfo;
    @SerializedName("useful_links")
    public String usefulLinks;
}
