package ru.katevpy.coursesync.groups;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.GroupParticipantItem;
import ru.katevpy.coursesync.shared.util.Result;
import ru.katevpy.coursesync.ui.ErrorUi;
import ru.katevpy.coursesync.ui.ListSpacingDecoration;

public class GroupMembersFragment extends Fragment {

    private GroupMembersViewModel viewModel;
    private GroupMembersAdapter adapter;
    private ProgressBar progress;
    private TextView empty;
    private TextView error;
    private MaterialButton retry;
    private RecyclerView recycler;

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
        UUID groupId;
        try {
            groupId = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        progress = view.findViewById(R.id.groupMembersProgress);
        empty = view.findViewById(R.id.groupMembersEmpty);
        error = view.findViewById(R.id.groupMembersError);
        retry = view.findViewById(R.id.groupMembersRetry);
        recycler = view.findViewById(R.id.groupMembersRecycler);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        int spacing = getResources().getDimensionPixelSize(R.dimen.grid_1);
        recycler.addItemDecoration(new ListSpacingDecoration(spacing));

        adapter = new GroupMembersAdapter(item -> {
            String email = item.email != null ? item.email.trim() : "";
            if (email.isEmpty()) {
                return;
            }
            viewModel.setBlocked(email, item.isBlocked);
        });
        recycler.setAdapter(adapter);

        retry.setOnClickListener(v -> {
            error.setVisibility(View.GONE);
            retry.setVisibility(View.GONE);
            viewModel.loadParticipants();
        });

        viewModel = new ViewModelProvider(
                this,
                new GroupMembersViewModelFactory(requireContext().getApplicationContext(), groupId)
        ).get(GroupMembersViewModel.class);

        viewModel.getParticipantsResult().observe(getViewLifecycleOwner(), this::onParticipantsResult);
        viewModel.getToggleResult().observe(getViewLifecycleOwner(), this::onToggleResult);

        progress.setVisibility(View.VISIBLE);
        viewModel.loadParticipants();
    }

    private void onParticipantsResult(@Nullable Result<List<GroupParticipantItem>> result) {
        if (result == null) {
            return;
        }
        progress.setVisibility(View.GONE);
        if (result instanceof Result.Success) {
            error.setVisibility(View.GONE);
            retry.setVisibility(View.GONE);
            List<GroupParticipantItem> list = ((Result.Success<List<GroupParticipantItem>>) result).data;
            if (list == null) {
                list = Collections.emptyList();
            }
            if (list.isEmpty()) {
                recycler.setVisibility(View.GONE);
                empty.setVisibility(View.VISIBLE);
                adapter.submitList(Collections.emptyList());
            } else {
                empty.setVisibility(View.GONE);
                recycler.setVisibility(View.VISIBLE);
                adapter.submitList(list);
            }
            return;
        }
        recycler.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<List<GroupParticipantItem>>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(
                        R.id.loginFragment,
                        null,
                        new NavOptions.Builder()
                                .setPopUpTo(R.id.groupsFragment, true)
                                .build());
                return;
            }
        }
        error.setText(R.string.group_members_load_error);
        error.setVisibility(View.VISIBLE);
        retry.setVisibility(View.VISIBLE);
    }

    private void onToggleResult(@Nullable Result<Void> result) {
        if (result == null) {
            return;
        }
        viewModel.consumeToggleResult();
        if (result instanceof Result.Success) {
            viewModel.loadParticipants();
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<Void>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(
                        R.id.loginFragment,
                        null,
                        new NavOptions.Builder()
                                .setPopUpTo(R.id.groupsFragment, true)
                                .build());
                return;
            }
        }
        ErrorUi.show(this, R.string.group_members_toggle_error, ErrorUi.Duration.SHORT);
    }
}
