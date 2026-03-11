package ru.katevpy.coursesync.groups;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.MainActivity;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CreateGroupResponse;
import ru.katevpy.coursesync.shared.util.Result;

public class CreateGroupFragment extends Fragment {

    private TextInputLayout groupNameLayout;
    private CreateGroupViewModel viewModel;

    public CreateGroupFragment() {
        super(R.layout.fragment_create_group);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        groupNameLayout = view.findViewById(R.id.groupNameLayout);
        viewModel = new ViewModelProvider(this, new CreateGroupViewModelFactory())
                .get(CreateGroupViewModel.class);

        Button btnCreate = view.findViewById(R.id.btnCreate);
        btnCreate.setOnClickListener(v -> {
            String name = groupNameLayout.getEditText() != null
                    ? groupNameLayout.getEditText().getText().toString()
                    : "";
            viewModel.createGroup(name);
        });

        viewModel.getCreateResult().observe(getViewLifecycleOwner(), this::onCreateResult);
    }

    private void onCreateResult(@Nullable Result<CreateGroupResponse> result) {
        if (result == null) {
            groupNameLayout.setError(null);
            return;
        }

        if (result instanceof Result.Success) {
            groupNameLayout.setError(null);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).refreshCurrentGroup();
            }
            NavController nav = NavHostFragment.findNavController(this);
            nav.navigateUp();
            return;
        }

        String message;
        if (result instanceof Result.LogicalError) {
            message = ((Result.LogicalError<CreateGroupResponse>) result).message;
        } else if (result instanceof Result.HttpError) {
            Result.HttpError<CreateGroupResponse> he = (Result.HttpError<CreateGroupResponse>) result;
            if (he.httpCode == 401) {
                App.getDeps().tokenStorage.clear();
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
                    Snackbar.make(v, R.string.create_group_server_error, Snackbar.LENGTH_LONG).show();
                }
                return;
            }
            message = getString(R.string.internal_error);
        } else {
            message = getString(R.string.network_error);
        }
        groupNameLayout.setError(message);
    }
}
