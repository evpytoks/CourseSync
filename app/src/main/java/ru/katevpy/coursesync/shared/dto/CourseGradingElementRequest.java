package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class CourseGradingElementRequest {
    @SerializedName("name")
    public String name;
    @SerializedName("coefficient")
    public Double coefficient;
    @SerializedName("block_grade")
    public Double blockGrade;

    public CourseGradingElementRequest(String name, Double coefficient, Double blockGrade) {
        this.name = name;
        this.coefficient = coefficient;
        this.blockGrade = blockGrade != null ? blockGrade : 0;
    }
}

