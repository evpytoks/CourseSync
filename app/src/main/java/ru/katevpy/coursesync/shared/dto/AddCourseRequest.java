package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

public final class AddCourseRequest {
    public String name;
    @SerializedName("general_info")
    public String generalInfo;
    @SerializedName("useful_links")
    public List<CourseUsefulLinkItem> usefulLinks;

    public AddCourseRequest(String name, String generalInfo, List<CourseUsefulLinkItem> usefulLinks) {
        this.name = name;
        this.generalInfo = generalInfo;
        this.usefulLinks = usefulLinks != null ? usefulLinks : Collections.emptyList();
    }
}
