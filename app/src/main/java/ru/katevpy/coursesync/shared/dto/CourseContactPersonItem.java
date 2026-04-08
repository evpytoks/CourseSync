package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class CourseContactPersonItem {
    @SerializedName("name")
    public String name;

    @SerializedName("contact_methods")
    public List<CourseContactMethodItem> contactMethods;

    public CourseContactPersonItem() {
    }

    public CourseContactPersonItem(String name, List<CourseContactMethodItem> contactMethods) {
        this.name = name;
        this.contactMethods = contactMethods;
    }
}
