package ru.katevpy.coursesync.shared.dto;

public final class UpdateCalendarEventRequest {
    public String name;
    public String date;
    public String description;

    public UpdateCalendarEventRequest(String name, String date, String description) {
        this.name = name;
        this.date = date;
        this.description = description != null ? description : "";
    }
}
