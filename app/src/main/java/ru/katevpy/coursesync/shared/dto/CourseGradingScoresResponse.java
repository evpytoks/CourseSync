package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class CourseGradingScoresResponse {
    public String name;

    public Integer count;

    @SerializedName("scores")
    public List<Double> scores;
}
