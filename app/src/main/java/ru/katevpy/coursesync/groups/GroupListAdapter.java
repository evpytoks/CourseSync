package ru.katevpy.coursesync.groups;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.GroupListItem;

public final class GroupListAdapter extends RecyclerView.Adapter<GroupListAdapter.VH> {

    public interface Listener {
        void onSelectGroup(@NonNull UUID groupId);

        void onCopyInviteCode(@NonNull UUID groupId);

        void onOwnerGroupActions(@NonNull View anchor, @NonNull UUID groupId, @NonNull String name);
    }

    private final List<GroupListItem> items = new ArrayList<>();
    private final Listener listener;

    public GroupListAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(@Nullable List<GroupListItem> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        GroupListItem g = items.get(position);
        String name = g.name != null ? g.name : "";
        h.title.setText(name);
        boolean owner = g.role != null && "owner".equalsIgnoreCase(g.role.trim());
        h.subtitle.setText(owner ? h.itemView.getContext().getString(R.string.group_role_owner)
                : h.itemView.getContext().getString(R.string.group_role_member));
        UUID groupId = g.id;
        h.itemView.setOnClickListener(v -> {
            if (groupId != null) {
                listener.onSelectGroup(groupId);
            }
        });
        if (owner && groupId != null) {
            h.actions.setVisibility(View.VISIBLE);
            h.copy.setOnClickListener(v -> listener.onCopyInviteCode(groupId));
            h.edit.setOnClickListener(v -> listener.onOwnerGroupActions(h.edit, groupId, name));
        } else {
            h.actions.setVisibility(View.GONE);
            h.copy.setOnClickListener(null);
            h.edit.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        final LinearLayout actions;
        final ImageButton copy;
        final ImageButton edit;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.groupRowTitle);
            subtitle = itemView.findViewById(R.id.groupRowSubtitle);
            actions = itemView.findViewById(R.id.groupRowActions);
            copy = itemView.findViewById(R.id.groupRowCopy);
            edit = itemView.findViewById(R.id.groupRowEdit);
        }
    }
}
