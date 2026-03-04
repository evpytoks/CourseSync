package ru.katevpy.coursesync.shared.dto;

public final class RefreshRequest {
    public final String RefreshToken;

    public RefreshRequest(String refreshToken) {
        this.RefreshToken = refreshToken;
    }
}
