package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class SaveCourseGradingRequest {
    @SerializedName("text")
    public String text;
    @SerializedName("elements")
    public List<CourseGradingElementRequest> elements;

    public SaveCourseGradingRequest(String text, List<CourseGradingElementRequest> elements) {
        this.text = text != null ? text : "";
        this.elements = elements;
    }
}

