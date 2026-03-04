package ru.katevpy.coursesync.shared.dto;

import com.google.gson.annotations.SerializedName;

public final class SendCodeResponse {
    @SerializedName(value = "requestId", alternate = {"RequestId"})
    public String requestId;

    @SerializedName(value = "expiresAt", alternate = {"ExpiresAt"})
    public String expiresAt;
}
