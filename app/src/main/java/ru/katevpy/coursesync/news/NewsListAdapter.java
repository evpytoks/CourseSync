package ru.katevpy.coursesync.news;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.NewsListItem;

public final class NewsListAdapter extends RecyclerView.Adapter<NewsListAdapter.VH> {

    public interface OnNewsClickListener {
        void onNewsClick(@NonNull UUID newsId);
    }

    private final List<NewsListItem> items = new ArrayList<>();
    private final OnNewsClickListener listener;

    public NewsListAdapter(OnNewsClickListener listener) {
        this.listener = listener;
    }

    public void submitList(@Nullable List<NewsListItem> list) {
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
        NewsListItem item = items.get(position);
        h.title.setText(item.name != null ? item.name : "");
        String sub = formatCreatedAt(item.createdAt);
        if (sub != null && !sub.isEmpty()) {
            h.subtitle.setVisibility(View.VISIBLE);
            h.subtitle.setText(sub);
        } else {
            h.subtitle.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(v -> {
            if (item.id != null) {
                listener.onNewsClick(item.id);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Nullable
    private static String formatCreatedAt(@Nullable String createdAt) {
        if (createdAt == null || createdAt.isEmpty()) {
            return null;
        }
        try {
            if (createdAt.length() >= 10) {
                LocalDate d = LocalDate.parse(createdAt.substring(0, 10));
                return d.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("ru")));
            }
        } catch (DateTimeParseException ignored) {
        }
        return createdAt.length() >= 10 ? createdAt.substring(0, 10) : createdAt;
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
