package ru.katevpy.coursesync.shared.dto;

public final class AddNewsRequest {
    public String name;
    public String description;

    public AddNewsRequest(String name, String description) {
        this.name = name;
        this.description = description != null ? description : "";
    }
}
