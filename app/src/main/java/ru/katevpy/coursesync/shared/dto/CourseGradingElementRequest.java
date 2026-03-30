package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class CourseGradingElementRequest {
    @SerializedName("name")
    public String name;
    @SerializedName("coefficient")
    public Double coefficient;

    public CourseGradingElementRequest(String name, Double coefficient) {
        this.name = name;
        this.coefficient = coefficient;
    }
}

