package ru.katevpy.coursesync.shared.dto;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class UsefulLinksJsonDeserializer implements JsonDeserializer<List<CourseUsefulLinkItem>> {

    private static final int TITLE_MAX = 50;
    private static final int URL_MAX = 200;

    @Override
    public List<CourseUsefulLinkItem> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
        List<CourseUsefulLinkItem> out = new ArrayList<>();
        if (json == null || json.isJsonNull()) {
            return out;
        }
        JsonElement root = json;
        if (json.isJsonPrimitive()) {
            JsonPrimitive prim = json.getAsJsonPrimitive();
            if (!prim.isString()) {
                return out;
            }
            String s = prim.getAsString();
            if (s == null) {
                return out;
            }
            s = s.trim();
            if (s.isEmpty()) {
                return out;
            }
            if (s.startsWith("[")) {
                try {
                    root = JsonParser.parseString(s);
                } catch (Exception e) {
                    return out;
                }
            } else {
                out.add(legacyItem(s));
                return out;
            }
        }
        if (!root.isJsonArray()) {
            return out;
        }
        JsonArray arr = root.getAsJsonArray();
        for (JsonElement el : arr) {
            if (el == null || el.isJsonNull()) {
                continue;
            }
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                String u = el.getAsString();
                if (u != null && !u.trim().isEmpty()) {
                    out.add(legacyItem(u));
                }
                continue;
            }
            if (!el.isJsonObject()) {
                continue;
            }
            CourseUsefulLinkItem item = fromObject(el.getAsJsonObject());
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    private static CourseUsefulLinkItem fromObject(JsonObject o) {
        String title = firstStringIgnoreCase(o, "title", "description", "name", "label", "text");
        String url = firstStringIgnoreCase(o, "url", "link", "href", "uri");
        if (url == null) {
            url = "";
        }
        url = url.trim();
        if (url.isEmpty()) {
            return null;
        }
        if (url.length() > URL_MAX) {
            url = url.substring(0, URL_MAX);
        }
        if (title == null) {
            title = "";
        }
        title = title.trim();
        if (title.isEmpty()) {
            title = "Ссылка";
        } else if (title.length() > TITLE_MAX) {
            title = title.substring(0, TITLE_MAX);
        }
        return new CourseUsefulLinkItem(title, url);
    }

    private static String firstStringIgnoreCase(JsonObject o, String... keys) {
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isJsonNull()) {
                continue;
            }
            String kn = e.getKey();
            for (String wanted : keys) {
                if (kn.equalsIgnoreCase(wanted)) {
                    JsonElement v = e.getValue();
                    if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                        return v.getAsString();
                    }
                    if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                        return v.getAsString();
                    }
                }
            }
        }
        return null;
    }

    private static CourseUsefulLinkItem legacyItem(String raw) {
        String u = raw.trim();
        if (u.length() > URL_MAX) {
            u = u.substring(0, URL_MAX);
        }
        return new CourseUsefulLinkItem("Ссылка", u);
    }
}
