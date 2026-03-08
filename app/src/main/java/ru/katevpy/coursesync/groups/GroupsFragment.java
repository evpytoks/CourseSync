package ru.katevpy.coursesync.groups;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
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
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.MainActivity;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.ChooseGroupResponse;
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
        viewModel.getChooseResult().observe(getViewLifecycleOwner(), this::onChooseResult);

        viewModel.loadGroups();
    }

    private void onChooseResult(@Nullable Result<ChooseGroupResponse> result) {
        if (result == null) return;

        if (result instanceof Result.Success) {
            ChooseGroupResponse data = ((Result.Success<ChooseGroupResponse>) result).data;
            if (data != null && data.name != null && data.id != null && getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).setSelectedGroupAndPersist(data.id, data.name);
            }
            return;
        }

        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<ChooseGroupResponse>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).clearSelectedGroupAndPersist();
                }
                NavController nav = NavHostFragment.findNavController(this);
                NavOptions opts = new NavOptions.Builder()
                        .setPopUpTo(R.id.groupsFragment, true)
                        .build();
                nav.navigate(R.id.loginFragment, null, opts);
                return;
            }
            if (code == 500) {
                Snackbar.make(groupsContainer, R.string.choose_group_error, Snackbar.LENGTH_LONG).show();
                return;
            }
        }

        Snackbar.make(groupsContainer, R.string.internal_error, Snackbar.LENGTH_LONG).show();
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
            int iconPadding = (int) (12 * getResources().getDisplayMetrics().density);
            for (GroupListItem g : groups) {
                FrameLayout wrapper = new FrameLayout(requireContext());
                LinearLayout.LayoutParams wrapperLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                wrapperLp.bottomMargin = marginBottom;
                wrapper.setLayoutParams(wrapperLp);

                MaterialButton btn = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                btn.setText(g.name);
                FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                btn.setLayoutParams(btnLp);
                java.util.UUID groupId = g.id;
                btn.setOnClickListener(v -> viewModel.chooseGroup(groupId));
                wrapper.addView(btn);

                LinearLayout iconsRow = new LinearLayout(requireContext());
                iconsRow.setOrientation(LinearLayout.HORIZONTAL);
                iconsRow.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
                FrameLayout.LayoutParams iconsLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                iconsLp.gravity = android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL;
                iconsRow.setLayoutParams(iconsLp);

                boolean isOwner = "owner".equalsIgnoreCase(g.role) && g.groupCode != null && !g.groupCode.isEmpty();
                if (isOwner) {
                    ImageButton copyBtn = new ImageButton(requireContext());
                    copyBtn.setImageResource(R.drawable.ic_content_copy);
                    copyBtn.setBackground(null);
                    copyBtn.setContentDescription(getString(R.string.copy_invite_code));
                    copyBtn.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
                    LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    copyBtn.setLayoutParams(copyLp);
                    String code = g.groupCode;
                    copyBtn.setOnClickListener(v -> {
                        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null && code != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("invite_code", code));
                            Snackbar.make(groupsContainer, R.string.invite_code_copied, Snackbar.LENGTH_SHORT).show();
                        }
                    });
                    iconsRow.addView(copyBtn);
                }
                ImageButton editBtn = new ImageButton(requireContext());
                editBtn.setImageResource(R.drawable.ic_edit);
                editBtn.setBackground(null);
                editBtn.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
                LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                editBtn.setLayoutParams(editLp);
                java.util.UUID editGroupId = g.id;
                String editGroupName = g.name != null ? g.name : "";
                editBtn.setOnClickListener(v -> {
                    Bundle args = new Bundle();
                    args.putString("groupId", editGroupId.toString());
                    args.putString("groupName", editGroupName);
                    NavController nav = NavHostFragment.findNavController(GroupsFragment.this);
                    nav.navigate(R.id.action_groupsFragment_to_editGroupFragment, args);
                });
                iconsRow.addView(editBtn);
                wrapper.addView(iconsRow);
                groupsContainer.addView(wrapper);
            }
            return;
        }

        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<List<GroupListItem>>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).clearSelectedGroupAndPersist();
                }
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
