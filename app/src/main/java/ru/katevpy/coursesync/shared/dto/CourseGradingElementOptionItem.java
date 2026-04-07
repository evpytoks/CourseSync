package ru.katevpy.coursesync.shared.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

public final class CourseGradingElementOptionItem {
    @SerializedName("id")
    public String id;

    @SerializedName("name")
    public String name;

    @Nullable
    public UUID resolveId() {
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
