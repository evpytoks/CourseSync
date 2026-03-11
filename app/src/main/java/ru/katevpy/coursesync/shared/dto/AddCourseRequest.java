package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class AddCourseRequest {
    public String name;
    @SerializedName("general_info")
    public String generalInfo;
    @SerializedName("useful_links")
    public String usefulLinks;

    public AddCourseRequest(String name, String generalInfo, String usefulLinks) {
        this.name = name;
        this.generalInfo = generalInfo;
        this.usefulLinks = usefulLinks;
    }
}
