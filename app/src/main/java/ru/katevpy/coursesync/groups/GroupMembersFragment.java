package ru.katevpy.coursesync.groups;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import java.util.UUID;

import ru.katevpy.coursesync.R;

public class GroupMembersFragment extends Fragment {

    public GroupMembersFragment() {
        super(R.layout.fragment_group_members);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        if (args == null) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        String idStr = args.getString("groupId");
        if (idStr == null || idStr.isEmpty()) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        try {
            UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            NavHostFragment.findNavController(this).navigateUp();
        }
    }
}
