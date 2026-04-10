package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

public final class AddCalendarEventRequest {
    @SerializedName("group_id")
    public UUID groupId;
    @SerializedName("course_id")
    public UUID courseId;
    @SerializedName("event_type")
    public String eventType;
    @SerializedName("name")
    public String name;
    @SerializedName("date")
    public String date;
    @SerializedName("description")
    public String description;

    public AddCalendarEventRequest(UUID groupId, UUID courseId, String eventType, String name, String date, String description) {
        this.groupId = groupId;
        this.courseId = courseId;
        this.eventType = eventType;
        this.name = name;
        this.date = date;
        this.description = description != null ? description : "";
    }
}
