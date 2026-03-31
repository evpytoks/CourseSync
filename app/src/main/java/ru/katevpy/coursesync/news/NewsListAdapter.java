package ru.katevpy.coursesync.news;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

    private static final int TITLE_MAX = 100;

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
        h.title.setText(listTitle(item));
        String sub = listSubtitle(item);
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

    @NonNull
    private static String listTitle(@NonNull NewsListItem item) {
        String fromText = firstLinePreview(item.text);
        if (!fromText.isEmpty()) {
            return fromText;
        }
        return item.section != null ? item.section : "";
    }

    @NonNull
    private static String firstLinePreview(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String line = text.split("\\R", 2)[0].trim();
        if (line.length() > TITLE_MAX) {
            return line.substring(0, TITLE_MAX - 1) + "…";
        }
        return line;
    }

    @Nullable
    private static String listSubtitle(@NonNull NewsListItem item) {
        String datePart = formatListDate(item.time);
        String group = item.group != null ? item.group.trim() : "";
        if (!group.isEmpty() && datePart != null && !datePart.isEmpty()) {
            return group + " · " + datePart;
        }
        if (!group.isEmpty()) {
            return group;
        }
        return datePart;
    }

    @Nullable
    private static String formatListDate(@Nullable String isoTime) {
        if (isoTime == null || isoTime.isEmpty()) {
            return null;
        }
        String s = isoTime.trim();
        DateTimeFormatter out = DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("ru"));
        try {
            return OffsetDateTime.parse(s).format(out);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(s).atZone(ZoneId.systemDefault()).format(out);
        } catch (DateTimeParseException ignored) {
        }
        if (s.length() >= 10) {
            return s.substring(0, 10);
        }
        return s;
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
