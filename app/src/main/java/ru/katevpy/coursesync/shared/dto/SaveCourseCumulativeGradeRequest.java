package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class SaveCourseCumulativeGradeRequest {
    @SerializedName("element_ids")
    public List<String> elementIds;

    @SerializedName("block_grade")
    public Double blockGrade;

    @SerializedName("auto_grade")
    public Double automatic;

    public SaveCourseCumulativeGradeRequest(List<String> elementIds, Double blockGrade, Double automatic) {
        this.elementIds = elementIds;
        this.blockGrade = blockGrade;
        this.automatic = automatic;
    }
}
