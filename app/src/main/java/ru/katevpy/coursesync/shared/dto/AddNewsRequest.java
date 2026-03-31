package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class AddNewsRequest {
    @SerializedName("text")
    public String text;

    public AddNewsRequest(String text) {
        this.text = text;
    }
}
