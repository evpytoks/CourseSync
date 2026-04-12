package ru.katevpy.coursesync.groups;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.GroupParticipantItem;

public final class GroupMembersAdapter extends RecyclerView.Adapter<GroupMembersAdapter.VH> {

    public interface Listener {
        void onAccessClick(@NonNull GroupParticipantItem item);
    }

    private final List<GroupParticipantItem> items = new ArrayList<>();
    private final Listener listener;

    public GroupMembersAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(@NonNull List<GroupParticipantItem> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group_member_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        GroupParticipantItem it = items.get(position);
        String em = it.email != null ? it.email : "";
        h.email.setText(em);
        if (it.isBlocked) {
            h.accessBtn.setImageResource(R.drawable.ic_person_blocked_24);
            h.accessBtn.setContentDescription(
                    h.accessBtn.getContext().getString(R.string.cd_group_member_access_blocked));
        } else {
            h.accessBtn.setImageResource(R.drawable.ic_person_24);
            h.accessBtn.setContentDescription(
                    h.accessBtn.getContext().getString(R.string.cd_group_member_access_active));
        }
        h.accessBtn.setOnClickListener(v -> listener.onAccessClick(it));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView email;
        final ImageButton accessBtn;

        VH(@NonNull View itemView) {
            super(itemView);
            email = itemView.findViewById(R.id.groupMemberEmail);
            accessBtn = itemView.findViewById(R.id.groupMemberAccessBtn);
        }
    }
}
