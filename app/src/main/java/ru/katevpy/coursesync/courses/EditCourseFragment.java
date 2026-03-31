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

import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CourseDetailsResponse;
import ru.katevpy.coursesync.shared.util.Result;

public class EditCourseFragment extends Fragment {

    private TextInputLayout nameLayout;
    private TextInputLayout generalLayout;
    private TextInputLayout linksLayout;
    private EditCourseViewModel viewModel;
    private UUID courseId;

    public EditCourseFragment() {
        super(R.layout.fragment_edit_course);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        nameLayout = view.findViewById(R.id.editCourseNameLayout);
        generalLayout = view.findViewById(R.id.editCourseGeneralLayout);
        linksLayout = view.findViewById(R.id.editCourseLinksLayout);

        Bundle args = getArguments();
        if (args == null) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        String idStr = args.getString("courseId");
        if (idStr == null || idStr.isEmpty()) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        try {
            courseId = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        viewModel = new ViewModelProvider(this, new EditCourseViewModelFactory()).get(EditCourseViewModel.class);
        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);
        viewModel.getUpdateResult().observe(getViewLifecycleOwner(), this::onUpdateResult);

        view.findViewById(R.id.btnSaveCourse).setOnClickListener(v -> submit());

        viewModel.loadCourse(courseId);
    }

    private void fillForm(CourseDetailsResponse data) {
        if (nameLayout.getEditText() != null) {
            nameLayout.getEditText().setText(data.name != null ? data.name : "");
        }
        if (generalLayout.getEditText() != null) {
            generalLayout.getEditText().setText(data.generalInfo != null ? data.generalInfo : "");
        }
        if (linksLayout.getEditText() != null) {
            linksLayout.getEditText().setText(data.usefulLinks != null ? data.usefulLinks : "");
        }
    }

    private void submit() {
        String name = nameLayout.getEditText() != null ? nameLayout.getEditText().getText().toString() : "";
        String general = generalLayout.getEditText() != null ? generalLayout.getEditText().getText().toString() : "";
        String links = linksLayout.getEditText() != null ? linksLayout.getEditText().getText().toString() : "";

        nameLayout.setError(null);
        generalLayout.setError(null);
        linksLayout.setError(null);

        String nameTrimmed = name.trim();
        String generalTrimmed = general.trim();
        String linksTrimmed = links.trim();

        int maxName = getResources().getInteger(R.integer.max_course_name_length);
        int maxGeneral = getResources().getInteger(R.integer.max_general_info_length);
        int maxLinks = getResources().getInteger(R.integer.max_useful_links_length);

        if (nameTrimmed.isEmpty()) {
            nameLayout.setError(getString(R.string.enter_course_name));
            return;
        }
        if (generalTrimmed.isEmpty()) {
            generalLayout.setError(getString(R.string.enter_general_info));
            return;
        }
        if (linksTrimmed.isEmpty()) {
            linksLayout.setError(getString(R.string.enter_useful_links));
            return;
        }
        if (nameTrimmed.length() > maxName) {
            nameLayout.setError(getString(R.string.course_name_max_length));
            return;
        }
        if (generalTrimmed.length() > maxGeneral) {
            generalLayout.setError(getString(R.string.general_info_max_length));
            return;
        }
        if (linksTrimmed.length() > maxLinks) {
            linksLayout.setError(getString(R.string.useful_links_max_length));
            return;
        }

        viewModel.updateCourse(courseId, nameTrimmed, generalTrimmed, linksTrimmed);
    }

    private void onLoadResult(@Nullable Result<CourseDetailsResponse> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
            CourseDetailsResponse data = ((Result.Success<CourseDetailsResponse>) result).data;
            if (data != null) {
                fillForm(data);
            }
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<CourseDetailsResponse>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            if (code == 403 || code == 404 || code == 400) {
                ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.course_load_error, ErrorUi.Duration.SHORT);
                return;
            }
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
    }

    private void onUpdateResult(@Nullable Result<Void> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
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
                ErrorUi.show(this, R.string.update_course_server_error, ErrorUi.Duration.LONG);
                return;
            }
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.LONG);
    }
}
