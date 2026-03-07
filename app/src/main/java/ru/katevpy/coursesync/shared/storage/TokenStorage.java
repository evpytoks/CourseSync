package ru.katevpy.coursesync.shared.storage;

import android.content.SharedPreferences;
import androidx.annotation.Nullable;

public final class TokenStorage {
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";

    private final SharedPreferences sp;

    public TokenStorage(SharedPreferences sp) { this.sp = sp; }

    public void save(String accessToken, String refreshToken) {
        sp.edit()
                .putString(KEY_ACCESS, accessToken)
                .putString(KEY_REFRESH, refreshToken)
                .commit();
    }

    @Nullable public String getAccess() { return sp.getString(KEY_ACCESS, null); }
    @Nullable public String getRefresh() { return sp.getString(KEY_REFRESH, null); }

    public void clear() {
        sp.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).apply();
    }
}
