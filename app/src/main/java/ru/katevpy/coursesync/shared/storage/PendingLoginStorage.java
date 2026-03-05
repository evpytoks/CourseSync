package ru.katevpy.coursesync.shared.storage;

import android.content.SharedPreferences;
import androidx.annotation.Nullable;

public final class PendingLoginStorage {
    private static final String KEY_EMAIL = "pending_email";
    private static final String KEY_REQ_ID = "pending_request_id";
    private static final String KEY_EXPIRES_AT_MS = "pending_expires_at_ms";

    private final SharedPreferences sp;

    public PendingLoginStorage(SharedPreferences sp) {
        this.sp = sp;
    }

    public void save(String email, String requestId, long expiresAtMs) {
        sp.edit()
                .putString(KEY_EMAIL, email)
                .putString(KEY_REQ_ID, requestId)
                .putLong(KEY_EXPIRES_AT_MS, expiresAtMs)
                .apply();
    }

    @Nullable public String getEmail() { return sp.getString(KEY_EMAIL, null); }
    @Nullable public String getRequestId() { return sp.getString(KEY_REQ_ID, null); }

    public long getExpiresAtMs() {
        return sp.getLong(KEY_EXPIRES_AT_MS, 0L);
    }

    public boolean isExpired() {
        long exp = sp.getLong(KEY_EXPIRES_AT_MS, 0L);
        return exp > 0 && System.currentTimeMillis() > exp;
    }

    public boolean hasPending() {
        return getEmail() != null && getRequestId() != null && !isExpired();
    }

    public void clear() {
        sp.edit()
                .remove(KEY_EMAIL)
                .remove(KEY_REQ_ID)
                .remove(KEY_EXPIRES_AT_MS)
                .apply();
    }
}