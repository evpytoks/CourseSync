package ru.katevpy.coursesync.groups;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.MainActivity;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.GroupDetailsResponse;
import ru.katevpy.coursesync.shared.dto.GroupListItem;
import ru.katevpy.coursesync.shared.repository.GroupRepository;
import ru.katevpy.coursesync.shared.util.Result;
import ru.katevpy.coursesync.ui.ErrorUi;
import ru.katevpy.coursesync.ui.ListSpacingDecoration;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class GroupsFragment extends Fragment {

    private GroupsViewModel viewModel;

    private RecyclerView groupsRecycler;
    private TextView emptyText;
    private View errorBanner;
    private GroupListAdapter listAdapter;

    public GroupsFragment() {
        super(R.layout.fragment_groups);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        groupsRecycler = view.findViewById(R.id.groupsRecycler);
        emptyText = view.findViewById(R.id.emptyText);
        errorBanner = view.findViewById(R.id.errorBanner);

        groupsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        int spacing = getResources().getDimensionPixelSize(R.dimen.grid_1);
        groupsRecycler.addItemDecoration(new ListSpacingDecoration(spacing));

        listAdapter = new GroupListAdapter(new GroupListAdapter.Listener() {
            @Override
            public void onGroupCardClick(@NonNull UUID groupId) {
                new Thread(() -> {
                    GroupRepository repo = new GroupRepository(App.getDeps().groupApi);
                    Result<Void> res = repo.chooseGroup(groupId);
                    requireActivity().runOnUiThread(() -> {
                        if (!(res instanceof Result.Success)) {
                            if (res instanceof Result.HttpError) {
                                int code = ((Result.HttpError<?>) res).httpCode;
                                if (code == 401) {
                                    App.getDeps().tokenStorage.clear();
                                    NavController nav = NavHostFragment.findNavController(GroupsFragment.this);
                                    NavOptions opts = new NavOptions.Builder()
                                            .setPopUpTo(R.id.groupsFragment, true)
                                            .build();
                                    nav.navigate(R.id.loginFragment, null, opts);
                                    return;
                                }
                            }
                            ErrorUi.show(GroupsFragment.this, R.string.choose_group_error, ErrorUi.Duration.SHORT);
                            return;
                        }
                        android.app.Activity a = requireActivity();
                        if (a instanceof MainActivity) {
                            ((MainActivity) a).refreshCurrentGroup();
                            ((MainActivity) a).scheduleOpenCoursesTab();
                        }
                    });
                }).start();
            }

            @Override
            public void onCopyInviteCode(@NonNull UUID groupId) {
                copyInviteCode(groupId);
            }

            @Override
            public void onOwnerGroupActions(@NonNull View anchor, @NonNull UUID groupId, @NonNull String name) {
                PopupMenu popup = new PopupMenu(requireContext(), anchor);
                popup.getMenuInflater().inflate(R.menu.group_owner_actions, popup.getMenu());
                popup.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.action_owner_members_list) {
                        Bundle args = new Bundle();
                        args.putString("groupId", groupId.toString());
                        NavHostFragment.findNavController(GroupsFragment.this)
                                .navigate(R.id.action_groupsFragment_to_groupMembersFragment, args);
                        return true;
                    }
                    if (itemId == R.id.action_owner_edit_group) {
                        openEditGroup(groupId, name);
                        return true;
                    }
                    if (itemId == R.id.action_owner_delete_group) {
                        showDeleteGroupDialog(groupId);
                        return true;
                    }
                    return false;
                });
                popup.show();
            }

            @Override
            public void onLeaveGroup(@NonNull UUID groupId) {
                showLeaveGroupDialog(groupId);
            }
        });
        groupsRecycler.setAdapter(listAdapter);

        viewModel = new ViewModelProvider(
                this,
                new GroupsViewModelFactory(requireContext().getApplicationContext())
        ).get(GroupsViewModel.class);

        viewModel.getGroupsResult().observe(getViewLifecycleOwner(), this::renderGroupsResult);
        viewModel.getDeleteGroupResult().observe(getViewLifecycleOwner(), this::onDeleteGroupResult);
        viewModel.getLeaveGroupResult().observe(getViewLifecycleOwner(), this::onLeaveGroupResult);

        viewModel.loadGroups();
    }

    private void openEditGroup(@NonNull UUID groupId, @NonNull String name) {
        Bundle args = new Bundle();
        args.putString("groupId", groupId.toString());
        args.putString("groupName", name);
        NavHostFragment.findNavController(GroupsFragment.this)
                .navigate(R.id.action_groupsFragment_to_editGroupFragment, args);
    }

    private void showDeleteGroupDialog(@NonNull UUID groupId) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.delete_group_dialog_message)
                .setPositiveButton(R.string.event_yes, (dialog, which) -> viewModel.deleteGroup(groupId))
                .setNegativeButton(R.string.event_no, null)
                .show();
    }

    private void showLeaveGroupDialog(@NonNull UUID groupId) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.leave_group_dialog_message)
                .setPositiveButton(R.string.event_yes, (dialog, which) -> viewModel.leaveGroup(groupId))
                .setNegativeButton(R.string.event_no, null)
                .show();
    }

    private void onLeaveGroupResult(@Nullable Result<Void> result) {
        if (result == null) {
            return;
        }
        if (result instanceof Result.Success) {
            viewModel.loadGroups();
            android.app.Activity a = requireActivity();
            if (a instanceof MainActivity) {
                ((MainActivity) a).refreshCurrentGroup();
            }
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<Void>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavController nav = NavHostFragment.findNavController(this);
                NavOptions opts = new NavOptions.Builder()
                        .setPopUpTo(R.id.groupsFragment, true)
                        .build();
                nav.navigate(R.id.loginFragment, null, opts);
                return;
            }
            if (code == 403 || code == 404 || code == 500) {
                ErrorUi.show(this, R.string.leave_group_server_error, ErrorUi.Duration.LONG);
                return;
            }
        }
        if (result instanceof Result.NetworkError) {
            ErrorUi.show(this, R.string.network_error, ErrorUi.Duration.SHORT);
            return;
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.LONG);
    }

    private void onDeleteGroupResult(@Nullable Result<Void> result) {
        if (result == null) {
            return;
        }
        if (result instanceof Result.Success) {
            viewModel.loadGroups();
            android.app.Activity a = requireActivity();
            if (a instanceof MainActivity) {
                ((MainActivity) a).refreshCurrentGroup();
            }
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<Void>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavController nav = NavHostFragment.findNavController(this);
                NavOptions opts = new NavOptions.Builder()
                        .setPopUpTo(R.id.groupsFragment, true)
                        .build();
                nav.navigate(R.id.loginFragment, null, opts);
                return;
            }
            if (code == 403 || code == 404 || code == 500) {
                ErrorUi.show(this, R.string.delete_group_server_error, ErrorUi.Duration.LONG);
                return;
            }
        }
        if (result instanceof Result.NetworkError) {
            ErrorUi.show(this, R.string.network_error, ErrorUi.Duration.SHORT);
            return;
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.LONG);
    }

    private void copyInviteCode(@NonNull UUID groupId) {
        new Thread(() -> {
            GroupRepository repo = new GroupRepository(App.getDeps().groupApi);
            repo.chooseGroup(groupId);
            Result<GroupDetailsResponse> res = repo.getCurrentGroup();
            if (res instanceof Result.Success && ((Result.Success<GroupDetailsResponse>) res).data != null) {
                GroupDetailsResponse data = ((Result.Success<GroupDetailsResponse>) res).data;
                String code = data.groupCode;
                if (code != null && !code.isEmpty()) {
                    requireActivity().runOnUiThread(() -> {
                        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("invite_code", code));
                            ErrorUi.show(GroupsFragment.this, R.string.invite_code_copied, ErrorUi.Duration.SHORT);
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.loadGroups();
    }

    private void renderGroupsResult(Result<List<GroupListItem>> result) {
        listAdapter.submitList(null);

        if (result == null) {
            ErrorUi.hideErrorBanner(errorBanner);
            emptyText.setVisibility(View.GONE);
            return;
        }

        if (result instanceof Result.Success) {
            ErrorUi.hideErrorBanner(errorBanner);
            List<GroupListItem> groups = ((Result.Success<List<GroupListItem>>) result).data;
            if (groups == null || groups.isEmpty()) {
                emptyText.setText(R.string.no_groups);
                emptyText.setVisibility(View.VISIBLE);
                groupsRecycler.setVisibility(View.GONE);
                return;
            }
            emptyText.setVisibility(View.GONE);
            groupsRecycler.setVisibility(View.VISIBLE);
            listAdapter.submitList(groups);
            return;
        }

        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<List<GroupListItem>>) result).httpCode;
            if (code == 401) {
                ErrorUi.hideErrorBanner(errorBanner);
                App.getDeps().tokenStorage.clear();
                NavController nav = NavHostFragment.findNavController(this);
                NavOptions opts = new NavOptions.Builder()
                        .setPopUpTo(R.id.groupsFragment, true)
                        .build();
                nav.navigate(R.id.loginFragment, null, opts);
                return;
            }
            ErrorUi.showErrorBanner(errorBanner, R.string.groups_load_error, () -> viewModel.loadGroups());
        } else if (result instanceof Result.NetworkError) {
            ErrorUi.showErrorBanner(errorBanner, R.string.groups_load_error, () -> viewModel.loadGroups());
        } else {
            String msg = ((Result.LogicalError<List<GroupListItem>>) result).message;
            ErrorUi.showErrorBanner(errorBanner, msg != null ? msg : getString(R.string.internal_error), () -> viewModel.loadGroups());
        }
        emptyText.setVisibility(View.GONE);
        groupsRecycler.setVisibility(View.GONE);
    }
}
