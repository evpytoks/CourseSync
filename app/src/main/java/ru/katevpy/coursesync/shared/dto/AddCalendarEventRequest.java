package ru.katevpy.coursesync.shared.dto;

public final class AddCalendarEventRequest {
    public String name;
    public String date;
    public String description;

    public AddCalendarEventRequest(String name, String date, String description) {
        this.name = name;
        this.date = date;
        this.description = description != null ? description : "";
    }
}
