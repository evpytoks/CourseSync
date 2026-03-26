package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class CoursePersonalMaterialListResponse {
    @SerializedName("materials")
    public List<CoursePersonalMaterialListItem> materials;
}

