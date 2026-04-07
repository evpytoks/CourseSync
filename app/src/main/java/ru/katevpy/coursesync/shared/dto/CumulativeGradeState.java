package ru.katevpy.coursesync.shared.dto;

import androidx.annotation.Nullable;

public final class CumulativeGradeState {
    @Nullable
    public final Boolean configured;
    @Nullable
    public final CourseCumulativeGradeResponse response;

    public CumulativeGradeState(@Nullable Boolean configured, @Nullable CourseCumulativeGradeResponse response) {
        this.configured = configured;
        this.response = response;
    }
}
