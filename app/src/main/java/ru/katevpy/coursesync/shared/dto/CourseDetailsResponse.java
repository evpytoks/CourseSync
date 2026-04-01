package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.UUID;

public final class CourseDetailsResponse {
    @SerializedName(value = "id", alternate = {"Id", "ID"})
    public UUID id;
    public String name;
    @SerializedName("general_info")
    public String generalInfo;
    @SerializedName("useful_links")
    @JsonAdapter(UsefulLinksJsonDeserializer.class)
    public List<CourseUsefulLinkItem> usefulLinks;
}
