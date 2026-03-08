package ru.katevpy.coursesync.groups;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.MainActivity;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.GroupChangeResponse;
import ru.katevpy.coursesync.shared.util.Result;

public class EditGroupFragment extends Fragment {

    private TextInputLayout groupNameLayout;
    private EditGroupViewModel viewModel;
    private UUID groupId;

    public EditGroupFragment() {
        super(R.layout.fragment_edit_group);
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
        String groupName = args.getString("groupName", "");
        if (idStr == null || idStr.isEmpty()) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        try {
            groupId = UUID.fromString(idStr);
        } catch (Exception e) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        groupNameLayout = view.findViewById(R.id.groupNameLayout);
        if (groupNameLayout.getEditText() != null) {
            groupNameLayout.getEditText().setText(groupName);
        }

        viewModel = new ViewModelProvider(this, new EditGroupViewModelFactory())
                .get(EditGroupViewModel.class);

        view.findViewById(R.id.btnSave).setOnClickListener(v -> {
            String name = groupNameLayout.getEditText() != null
                    ? groupNameLayout.getEditText().getText().toString()
                    : "";
            viewModel.saveGroupName(groupId, name);
        });

        viewModel.getChangeResult().observe(getViewLifecycleOwner(), this::onChangeResult);
    }

    private void onChangeResult(@Nullable Result<GroupChangeResponse> result) {
        if (result == null) {
            groupNameLayout.setError(null);
            return;
        }

        if (result instanceof Result.Success) {
            groupNameLayout.setError(null);
            GroupChangeResponse data = ((Result.Success<GroupChangeResponse>) result).data;
            if (data != null && data.name != null) {
                android.app.Activity act = requireActivity();
                if (act instanceof MainActivity) {
                    ((MainActivity) act).updateSelectedGroupNameIfIdMatches(groupId.toString(), data.name);
                }
            }
            NavController nav = NavHostFragment.findNavController(this);
            nav.navigateUp();
            return;
        }

        if (result instanceof Result.LogicalError) {
            groupNameLayout.setError(((Result.LogicalError<GroupChangeResponse>) result).message);
            return;
        }

        if (result instanceof Result.HttpError) {
            Result.HttpError<GroupChangeResponse> he = (Result.HttpError<GroupChangeResponse>) result;
            if (he.httpCode == 401) {
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
            if (he.httpCode == 400) {
                groupNameLayout.setError(getString(R.string.create_group_validation_error));
                return;
            }
            if (he.httpCode == 500) {
                groupNameLayout.setError(null);
                View v = getView();
                if (v != null) {
                    Snackbar.make(v, R.string.update_group_name_error, Snackbar.LENGTH_LONG).show();
                }
                return;
            }
        }

        groupNameLayout.setError(null);
        View v = getView();
        if (v != null) {
            Snackbar.make(v, R.string.internal_error, Snackbar.LENGTH_LONG).show();
        }
    }
}
