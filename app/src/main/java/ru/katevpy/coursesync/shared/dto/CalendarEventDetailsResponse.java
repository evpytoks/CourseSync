package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

public final class CalendarEventDetailsResponse {
    @SerializedName(value = "group_id", alternate = {"GroupId"})
    public UUID groupId;
    @SerializedName(value = "group_name", alternate = {"GroupName"})
    public String groupName;
    @SerializedName(value = "course_id", alternate = {"CourseId"})
    public UUID courseId;
    @SerializedName(value = "course_name", alternate = {"CourseName"})
    public String courseName;
    @SerializedName(value = "event_type", alternate = {"EventType"})
    public String eventType;
    @SerializedName(value = "event_color", alternate = {"EventColor"})
    public String eventColor;
    @SerializedName(value = "name", alternate = {"Name"})
    public String name;
    @SerializedName(value = "date", alternate = {"Date"})
    public String date;
    @SerializedName(value = "description", alternate = {"Description"})
    public String description;
    @SerializedName(value = "is_done", alternate = {"IsDone"})
    public boolean isDone;
}
