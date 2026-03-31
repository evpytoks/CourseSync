package ru.katevpy.coursesync.news;

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
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_news_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        NewsListItem item = items.get(position);
        String meta = formatMeta(item.group, item.section);
        if (meta.isEmpty()) {
            h.meta.setVisibility(View.GONE);
        } else {
            h.meta.setVisibility(View.VISIBLE);
            h.meta.setText(meta);
        }
        String timeStr = NewsDateTime.format(item.time);
        if (timeStr == null || timeStr.isEmpty()) {
            h.time.setVisibility(View.GONE);
        } else {
            h.time.setVisibility(View.VISIBLE);
            h.time.setText(timeStr);
        }
        String body = item.text != null ? item.text.trim() : "";
        if (body.isEmpty()) {
            h.body.setVisibility(View.GONE);
        } else {
            h.body.setVisibility(View.VISIBLE);
            h.body.setText(body);
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

    @NonNull
    private static String formatMeta(@Nullable String group, @Nullable String section) {
        String g = group != null ? group.trim() : "";
        String s = section != null ? section.trim() : "";
        if (!g.isEmpty() && !s.isEmpty()) {
            return g + " · " + s;
        }
        if (!g.isEmpty()) {
            return g;
        }
        return s;
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView meta;
        final TextView time;
        final TextView body;

        VH(@NonNull View itemView) {
            super(itemView);
            meta = itemView.findViewById(R.id.newsItemMeta);
            time = itemView.findViewById(R.id.newsItemTime);
            body = itemView.findViewById(R.id.newsItemBody);
        }
    }
}
