package ru.katevpy.coursesync.calendar;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

final class CalendarDropdownAdapter extends ArrayAdapter<String> {

    private final List<String> full;

    CalendarDropdownAdapter(Context context, int layout, List<String> items) {
        super(context, layout, new ArrayList<>(items));
        full = new ArrayList<>(items);
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults r = new FilterResults();
                r.values = full;
                r.count = full.size();
                return r;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                clear();
                addAll(full);
                notifyDataSetChanged();
            }
        };
    }
}
