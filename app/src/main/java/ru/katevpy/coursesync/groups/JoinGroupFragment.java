package ru.katevpy.coursesync.groups;

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

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.MainActivity;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.GroupJoinResponse;
import ru.katevpy.coursesync.shared.util.Result;

public class JoinGroupFragment extends Fragment {

    private TextInputLayout groupCodeLayout;
    private JoinGroupViewModel viewModel;

    public JoinGroupFragment() {
        super(R.layout.fragment_join_group);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        groupCodeLayout = view.findViewById(R.id.groupCodeLayout);
        viewModel = new ViewModelProvider(this, new JoinGroupViewModelFactory())
                .get(JoinGroupViewModel.class);

        view.findViewById(R.id.btnJoin).setOnClickListener(v -> {
            String code = groupCodeLayout.getEditText() != null
                    ? groupCodeLayout.getEditText().getText().toString()
                    : "";
            viewModel.joinGroup(code);
        });

        viewModel.getJoinResult().observe(getViewLifecycleOwner(), this::onJoinResult);
    }

    private void onJoinResult(@Nullable Result<GroupJoinResponse> result) {
        if (result == null) {
            groupCodeLayout.setError(null);
            return;
        }

        if (result instanceof Result.Success) {
            groupCodeLayout.setError(null);
            NavController nav = NavHostFragment.findNavController(this);
            nav.navigateUp();
            return;
        }

        String message;
        if (result instanceof Result.LogicalError) {
            message = ((Result.LogicalError<GroupJoinResponse>) result).message;
        } else if (result instanceof Result.HttpError) {
            Result.HttpError<GroupJoinResponse> he = (Result.HttpError<GroupJoinResponse>) result;
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
                groupCodeLayout.setError(getString(R.string.enter_code));
                return;
            }
            if (he.httpCode == 404) {
                groupCodeLayout.setError(getString(R.string.invalid_code));
                return;
            }
            if (he.httpCode == 500) {
                groupCodeLayout.setError(null);
                View v = getView();
                if (v != null) {
                    Snackbar.make(v, R.string.join_group_server_error, Snackbar.LENGTH_LONG).show();
                }
                return;
            }
            message = getString(R.string.internal_error);
        } else {
            message = getString(R.string.network_error);
        }
        groupCodeLayout.setError(message);
    }
}
