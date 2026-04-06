package ru.katevpy.coursesync.courses;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CourseDetailsResponse;
import ru.katevpy.coursesync.shared.dto.CourseUsefulLinkItem;
import ru.katevpy.coursesync.shared.util.Result;
import ru.katevpy.coursesync.ui.ErrorUi;

public class EditCourseFragment extends Fragment {

    private TextInputLayout nameLayout;
    private TextInputLayout generalLayout;
    private LinearLayout courseFormLinksList;
    private TextView courseFormLinksEmpty;
    private TextView courseFormLinksError;
    private final ArrayList<CourseUsefulLinkItem> editingLinks = new ArrayList<>();
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
        courseFormLinksList = view.findViewById(R.id.courseFormLinksList);
        courseFormLinksEmpty = view.findViewById(R.id.courseFormLinksEmpty);
        courseFormLinksError = view.findViewById(R.id.courseFormLinksError);

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

        view.findViewById(R.id.btnAddCourseLink).setOnClickListener(v -> showAddLinkDialog());
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
        editingLinks.clear();
        if (data.usefulLinks != null) {
            for (CourseUsefulLinkItem item : data.usefulLinks) {
                if (item == null) {
                    continue;
                }
                String u = item.url != null ? item.url.trim() : "";
                if (u.isEmpty()) {
                    continue;
                }
                String t = item.title != null ? item.title.trim() : "";
                if (t.isEmpty()) {
                    t = getString(R.string.link_default_title);
                }
                editingLinks.add(new CourseUsefulLinkItem(t, u));
            }
        }
        rebuildLinksList();
        clearLinksError();
    }

    private void rebuildLinksList() {
        courseFormLinksList.removeAllViews();
        if (editingLinks.isEmpty()) {
            courseFormLinksEmpty.setVisibility(View.VISIBLE);
            return;
        }
        courseFormLinksEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < editingLinks.size(); i++) {
            CourseUsefulLinkItem item = editingLinks.get(i);
            View row = inflater.inflate(R.layout.item_course_useful_link_edit_row, courseFormLinksList, false);
            TextView titleView = row.findViewById(R.id.editCourseLinkTitle);
            titleView.setText(item.title != null ? item.title : "");
            row.findViewById(R.id.editCourseLinkRemove).setOnClickListener(v -> {
                View parentRow = (View) v.getParent();
                int idx = courseFormLinksList.indexOfChild(parentRow);
                if (idx >= 0 && idx < editingLinks.size()) {
                    editingLinks.remove(idx);
                    rebuildLinksList();
                    clearLinksError();
                }
            });
            courseFormLinksList.addView(row);
        }
    }

    private void clearLinksError() {
        courseFormLinksError.setVisibility(View.GONE);
        courseFormLinksError.setText("");
    }

    private void setLinksError(@NonNull String message) {
        courseFormLinksError.setText(message);
        courseFormLinksError.setVisibility(View.VISIBLE);
    }

    private void showAddLinkDialog() {
        if (CourseUsefulLinksForm.isListFull(editingLinks)) {
            ErrorUi.show(this, R.string.useful_links_too_many_error, ErrorUi.Duration.SHORT);
            return;
        }
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_useful_link, null, false);
        TextInputLayout titleLayout = dialogView.findViewById(R.id.dialogLinkTitleLayout);
        TextInputLayout urlLayout = dialogView.findViewById(R.id.dialogLinkUrlLayout);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.add_useful_link_dialog_title)
                .setView(dialogView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.add_content, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            titleLayout.setError(null);
            urlLayout.setError(null);
            String t = titleLayout.getEditText() != null ? titleLayout.getEditText().getText().toString() : "";
            String u = urlLayout.getEditText() != null ? urlLayout.getEditText().getText().toString() : "";
            int et = CourseUsefulLinksForm.validateDraftTitle(t);
            if (et != 0) {
                titleLayout.setError(getString(et));
                return;
            }
            int eu = CourseUsefulLinksForm.validateDraftUrl(u);
            if (eu != 0) {
                urlLayout.setError(getString(eu));
                return;
            }
            if (CourseUsefulLinksForm.isListFull(editingLinks)) {
                ErrorUi.show(this, R.string.useful_links_too_many_error, ErrorUi.Duration.SHORT);
                return;
            }
            editingLinks.add(new CourseUsefulLinkItem(t.trim(), u.trim()));
            rebuildLinksList();
            clearLinksError();
            dialog.dismiss();
        }));

        dialog.show();
    }

    private void submit() {
        String name = nameLayout.getEditText() != null ? nameLayout.getEditText().getText().toString() : "";
        String general = generalLayout.getEditText() != null ? generalLayout.getEditText().getText().toString() : "";

        nameLayout.setError(null);
        generalLayout.setError(null);
        clearLinksError();

        String nameTrimmed = name.trim();
        String generalTrimmed = general.trim();

        int maxName = getResources().getInteger(R.integer.max_course_name_length);
        int maxGeneral = getResources().getInteger(R.integer.max_general_info_length);

        if (nameTrimmed.isEmpty()) {
            nameLayout.setError(getString(R.string.enter_course_name));
            return;
        }
        int linksErr = CourseUsefulLinksForm.validateForSubmit(editingLinks);
        if (linksErr != 0) {
            setLinksError(getString(linksErr));
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

        viewModel.updateCourse(courseId, nameTrimmed, generalTrimmed, new ArrayList<>(editingLinks));
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
