package ru.katevpy.coursesync.settings;

import androidx.annotation.Nullable;

public final class ColorHex {

    private ColorHex() {
    }

    public static boolean isValid(@Nullable String value) {
        if (value == null) {
            return false;
        }
        String s = value.trim();
        if (s.length() != 7 || s.charAt(0) != '#') {
            return false;
        }
        for (int i = 1; i < 7; i++) {
            char c = s.charAt(i);
            boolean d = c >= '0' && c <= '9';
            boolean a = (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!d && !a) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public static String normalizeOrNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        if (!s.startsWith("#") && s.length() == 6 && isValid("#" + s)) {
            return "#" + s;
        }
        return isValid(s) ? s : null;
    }
}
