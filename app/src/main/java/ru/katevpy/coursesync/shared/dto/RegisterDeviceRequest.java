package ru.katevpy.coursesync.shared.dto;

public final class RegisterDeviceRequest {
    public String platform;
    public String token;

    public RegisterDeviceRequest(String platform, String token) {
        this.platform = platform;
        this.token = token;
    }
}
