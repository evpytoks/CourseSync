package ru.katevpy.coursesync.courses;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CourseUsefulLinkItem;

final class CourseUsefulLinksForm {

    private static final int MAX_ITEMS = 50;
    private static final int TITLE_MAX = 50;
    private static final int URL_MIN = 1;
    private static final int URL_MAX = 200;

    private CourseUsefulLinksForm() {
    }

    @NonNull
    static String formatToMultiline(@Nullable List<CourseUsefulLinkItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (CourseUsefulLinkItem item : items) {
            if (item == null) {
                continue;
            }
            String t = item.title != null ? item.title.trim() : "";
            String u = item.url != null ? item.url.trim() : "";
            if (u.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            if (t.isEmpty()) {
                sb.append(u);
            } else {
                sb.append(t).append('|').append(u);
            }
        }
        return sb.toString();
    }

    @NonNull
    static List<CourseUsefulLinkItem> parseMultiline(@Nullable String text) {
        List<CourseUsefulLinkItem> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String line : text.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            int pipe = t.indexOf('|');
            if (pipe >= 0) {
                String title = t.substring(0, pipe).trim();
                String url = t.substring(pipe + 1).trim();
                if (url.isEmpty()) {
                    continue;
                }
                if (title.isEmpty()) {
                    title = url;
                }
                out.add(new CourseUsefulLinkItem(title, url));
            } else if (looksLikeUrl(t)) {
                out.add(new CourseUsefulLinkItem(t, t));
            }
        }
        return out;
    }

    private static boolean looksLikeUrl(@NonNull String s) {
        return s.regionMatches(true, 0, "http://", 0, 7)
                || s.regionMatches(true, 0, "https://", 0, 8);
    }

    static int validateForSubmit(@NonNull List<CourseUsefulLinkItem> items) {
        if (items.size() > MAX_ITEMS) {
            return R.string.useful_links_too_many_error;
        }
        for (CourseUsefulLinkItem item : items) {
            if (item == null) {
                return R.string.useful_link_fields_invalid;
            }
            String title = item.title != null ? item.title.trim() : "";
            String url = item.url != null ? item.url.trim() : "";
            if (title.length() > TITLE_MAX) {
                return R.string.useful_link_fields_invalid;
            }
            if (url.length() < URL_MIN || url.length() > URL_MAX) {
                return R.string.useful_link_fields_invalid;
            }
        }
        return 0;
    }

    static boolean isListFull(@Nullable List<?> items) {
        return items != null && items.size() >= MAX_ITEMS;
    }

    static int validateDraftTitle(@Nullable String title) {
        String t = title != null ? title.trim() : "";
        if (t.length() > TITLE_MAX) {
            return R.string.useful_link_title_too_long;
        }
        return 0;
    }

    static int validateDraftUrl(@Nullable String url) {
        String u = url != null ? url.trim() : "";
        if (u.length() < URL_MIN) {
            return R.string.useful_link_enter_url;
        }
        if (u.length() > URL_MAX) {
            return R.string.useful_link_url_too_long;
        }
        return 0;
    }
}
