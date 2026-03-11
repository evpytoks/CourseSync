package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class CourseListResponse {
    @SerializedName(value = "courses", alternate = {"Courses"})
    public List<CourseListItem> courses;
}
