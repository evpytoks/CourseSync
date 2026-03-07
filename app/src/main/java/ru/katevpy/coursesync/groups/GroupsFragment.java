package ru.katevpy.coursesync.groups;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;

import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.GroupListItem;

public class GroupsFragment extends Fragment {

    private GroupsViewModel viewModel;

    private LinearLayout groupsContainer;
    private TextView emptyText;

    public GroupsFragment() {
        super(R.layout.fragment_groups);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        groupsContainer = view.findViewById(R.id.groupsContainer);
        emptyText = view.findViewById(R.id.emptyText);

        viewModel = new ViewModelProvider(
                this,
                new GroupsViewModelFactory(requireContext().getApplicationContext())
        ).get(GroupsViewModel.class);

        viewModel.getGroups().observe(getViewLifecycleOwner(), this::renderGroups);

        viewModel.loadGroups();
    }

    private void renderGroups(List<GroupListItem> groups) {

        groupsContainer.removeAllViews();

        if (groups == null || groups.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            return;
        }

        emptyText.setVisibility(View.GONE);

        for (GroupListItem g : groups) {

            TextView tv = new TextView(requireContext());
            tv.setText(g.name);
            tv.setTextSize(18);

            groupsContainer.addView(tv);
        }
    }
}
