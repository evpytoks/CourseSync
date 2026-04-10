package ru.katevpy.coursesync.settings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.Arrays;

import ru.katevpy.coursesync.R;

public final class EventColorPresetAdapter extends ArrayAdapter<String> {

    public EventColorPresetAdapter(@NonNull Context context) {
        super(context, 0, Arrays.asList(EventColorPresets.PRESETS));
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return bindPresetRow(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return getView(position, convertView, parent);
    }

    @NonNull
    private View bindPresetRow(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View row = convertView;
        if (row == null || row.findViewById(R.id.presetSwatch) == null) {
            row = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_event_color_preset_dropdown, parent, false);
        }
        String hex = getItem(position);
        if (hex == null) {
            return row;
        }
        TextView label = row.findViewById(R.id.presetHex);
        View swatch = row.findViewById(R.id.presetSwatch);
        label.setText(hex);
        bindSwatch(swatch, hex);
        return row;
    }

    private static void bindSwatch(@NonNull View swatch, @NonNull String hex) {
        int fill;
        try {
            fill = Color.parseColor(hex);
        } catch (IllegalArgumentException e) {
            fill = ContextCompat.getColor(swatch.getContext(), R.color.calendar_event_type_default);
        }
        TypedValue tv = new TypedValue();
        swatch.getContext().getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorOutline, tv, true);
        int stroke = tv.data;
        int strokePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1f, swatch.getResources().getDisplayMetrics());
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        d.setStroke(strokePx, stroke);
        swatch.setBackground(d);
    }
}
