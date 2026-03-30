package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class UpdateCourseGradingScoresRequest {
    @SerializedName("name")
    public final String name;
    @SerializedName("scores")
    public final List<Double> scores;

    public UpdateCourseGradingScoresRequest(String name, List<Double> scores) {
        this.name = name != null ? name : "";
        this.scores = scores;
    }
}
