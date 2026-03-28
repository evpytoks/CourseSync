package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class CourseGradingElementsResponse {
    @SerializedName("elements")
    public List<CourseGradingElementItem> elements;
}
