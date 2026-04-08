package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class CourseContactMethodItem {
    @SerializedName("type")
    public String type;

    @SerializedName("value")
    public String value;

    public CourseContactMethodItem() {
    }

    public CourseContactMethodItem(String type, String value) {
        this.type = type;
        this.value = value;
    }
}
