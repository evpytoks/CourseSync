package ru.katevpy.coursesync.courses;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import ru.katevpy.coursesync.ui.ErrorUi;
import com.google.android.material.textfield.TextInputLayout;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.util.Result;

public class CreateCourseFragment extends Fragment {

    private TextInputLayout courseNameLayout;
    private TextInputLayout generalInfoLayout;
    private TextInputLayout usefulLinksLayout;
    private CreateCourseViewModel viewModel;

    public CreateCourseFragment() {
        super(R.layout.fragment_create_course);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        courseNameLayout = view.findViewById(R.id.courseNameLayout);
        generalInfoLayout = view.findViewById(R.id.generalInfoLayout);
        usefulLinksLayout = view.findViewById(R.id.usefulLinksLayout);

        int maxCourseName = getResources().getInteger(R.integer.max_course_name_length);
        int maxGeneralInfo = getResources().getInteger(R.integer.max_general_info_length);
        int maxUsefulLinks = getResources().getInteger(R.integer.max_useful_links_length);

        viewModel = new ViewModelProvider(
                this,
                new CreateCourseViewModelFactory(requireContext().getApplicationContext())
        ).get(CreateCourseViewModel.class);

        view.findViewById(R.id.btnCreate).setOnClickListener(v -> {
            String name = courseNameLayout.getEditText() != null
                    ? courseNameLayout.getEditText().getText().toString()
                    : "";
            String generalInfo = generalInfoLayout.getEditText() != null
                    ? generalInfoLayout.getEditText().getText().toString()
                    : "";
            String usefulLinks = usefulLinksLayout.getEditText() != null
                    ? usefulLinksLayout.getEditText().getText().toString()
                    : "";
            String nameTrimmed = name.trim();
            String generalInfoTrimmed = generalInfo.trim();
            String usefulLinksTrimmed = usefulLinks.trim();

            courseNameLayout.setError(null);
            generalInfoLayout.setError(null);
            usefulLinksLayout.setError(null);

            if (nameTrimmed.isEmpty()) {
                courseNameLayout.setError(getString(R.string.enter_course_name));
                return;
            }
            if (generalInfoTrimmed.isEmpty()) {
                generalInfoLayout.setError(getString(R.string.enter_general_info));
                return;
            }
            if (usefulLinksTrimmed.isEmpty()) {
                usefulLinksLayout.setError(getString(R.string.enter_useful_links));
                return;
            }
            if (nameTrimmed.length() > maxCourseName) {
                courseNameLayout.setError(getString(R.string.course_name_max_length));
                return;
            }
            if (generalInfoTrimmed.length() > maxGeneralInfo) {
                generalInfoLayout.setError(getString(R.string.general_info_max_length));
                return;
            }
            if (usefulLinksTrimmed.length() > maxUsefulLinks) {
                usefulLinksLayout.setError(getString(R.string.useful_links_max_length));
                return;
            }

            viewModel.createCourse(nameTrimmed, generalInfoTrimmed, usefulLinksTrimmed);
        });

        viewModel.getCreateResult().observe(getViewLifecycleOwner(), this::onCreateResult);
    }

    private void onCreateResult(@Nullable Result<Void> result) {
        if (result == null) {
            courseNameLayout.setError(null);
            generalInfoLayout.setError(null);
            usefulLinksLayout.setError(null);
            return;
        }

        if (result instanceof Result.Success) {
            courseNameLayout.setError(null);
            generalInfoLayout.setError(null);
            usefulLinksLayout.setError(null);
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<Void>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavOptions opts = new NavOptions.Builder()
                        .setPopUpTo(R.id.groupsFragment, true)
                        .build();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment, null, opts);
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.create_course_server_error, ErrorUi.Duration.LONG);
                return;
            }
        }

        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.LONG);
    }
}
