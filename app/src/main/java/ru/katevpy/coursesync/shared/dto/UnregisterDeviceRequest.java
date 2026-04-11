package ru.katevpy.coursesync.shared.dto;

public final class UnregisterDeviceRequest {
    public String token;

    public UnregisterDeviceRequest(String token) {
        this.token = token;
    }
}
