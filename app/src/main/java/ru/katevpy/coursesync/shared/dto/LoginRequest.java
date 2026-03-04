package ru.katevpy.coursesync.shared.dto;

public final class LoginRequest {
    public final String Email;
    public final String RequestId;
    public final String Code;

    public LoginRequest(String email, String requestId, String code) {
        this.Email = email;
        this.RequestId = requestId;
        this.Code = code;
    }
}
