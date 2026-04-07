package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class CourseGradingElementItem {
    public String name;
    @SerializedName("coefficient")
    public Double coefficient;
    @SerializedName(value = "block_grade", alternate = {"blockGrade", "BlockGrade"})
    public Double blockGrade;

    @SerializedName("count")
    public Integer count;

    @SerializedName("average_score")
    public Double averageScore;
}
