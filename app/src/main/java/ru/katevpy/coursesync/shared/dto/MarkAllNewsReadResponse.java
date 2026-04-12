package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class MarkAllNewsReadResponse {
    @SerializedName(value = "marked_count", alternate = {"MarkedCount"})
    public Integer markedCount;
}
