package ru.katevpy.coursesync.groups;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;

import java.util.List;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.GroupListItem;
import ru.katevpy.coursesync.shared.util.Result;

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

        viewModel.getGroupsResult().observe(getViewLifecycleOwner(), this::renderGroupsResult);

        viewModel.loadGroups();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.loadGroups();
    }

    private void renderGroupsResult(Result<List<GroupListItem>> result) {
        groupsContainer.removeAllViews();

        if (result == null) {
            emptyText.setVisibility(View.GONE);
            return;
        }

        if (result instanceof Result.Success) {
            List<GroupListItem> groups = ((Result.Success<List<GroupListItem>>) result).data;
            if (groups == null || groups.isEmpty()) {
                emptyText.setText(R.string.no_groups);
                emptyText.setVisibility(View.VISIBLE);
                return;
            }
            emptyText.setVisibility(View.GONE);
            int marginBottom = (int) (12 * getResources().getDisplayMetrics().density);
            for (GroupListItem g : groups) {
                MaterialButton btn = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                btn.setText(g.name);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = marginBottom;
                btn.setLayoutParams(lp);
                groupsContainer.addView(btn);
            }
            return;
        }

        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<List<GroupListItem>>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavController nav = NavHostFragment.findNavController(this);
                NavOptions opts = new NavOptions.Builder()
                        .setPopUpTo(R.id.groupsFragment, true)
                        .build();
                nav.navigate(R.id.loginFragment, null, opts);
                return;
            }
            emptyText.setText(R.string.groups_load_error);
        } else if (result instanceof Result.NetworkError) {
            emptyText.setText(R.string.groups_load_error);
        } else {
            emptyText.setText(((Result.LogicalError<List<GroupListItem>>) result).message);
        }
        emptyText.setVisibility(View.VISIBLE);
    }
}
