package ru.katevpy.coursesync.courses;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CourseListItem;

public final class CourseListAdapter extends RecyclerView.Adapter<CourseListAdapter.VH> {

    public interface OnCourseClickListener {
        void onCourseClick(@NonNull UUID courseId);
    }

    private final List<CourseListItem> items = new ArrayList<>();
    private final OnCourseClickListener listener;

    public CourseListAdapter(OnCourseClickListener listener) {
        this.listener = listener;
    }

    public void submitList(@Nullable List<CourseListItem> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_list_nav_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        CourseListItem item = items.get(position);
        h.title.setText(item.name != null ? item.name : "");
        h.subtitle.setVisibility(View.GONE);
        h.itemView.setOnClickListener(v -> {
            if (item.id != null) {
                listener.onCourseClick(item.id);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.listRowTitle);
            subtitle = itemView.findViewById(R.id.listRowSubtitle);
        }
    }
}
