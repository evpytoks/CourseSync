package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class CourseCumulativeGradeResponse {
    @SerializedName("value")
    public Double value;

    @SerializedName("block_grade")
    public Double blockGrade;

    @SerializedName("auto_grade")
    public Double automatic;

    @SerializedName("is_blocked")
    public Boolean isBlocked;

    @SerializedName("is_auto")
    public Boolean isAuto;

    @SerializedName("element_names")
    public List<String> elementNames;
}
