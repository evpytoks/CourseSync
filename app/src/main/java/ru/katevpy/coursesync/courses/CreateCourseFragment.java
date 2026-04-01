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

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CourseUsefulLinkItem;
import ru.katevpy.coursesync.shared.util.Result;
import ru.katevpy.coursesync.ui.ErrorUi;

public class CreateCourseFragment extends Fragment {

    private TextInputLayout courseNameLayout;
    private TextInputLayout generalInfoLayout;
    private LinearLayout courseFormLinksList;
    private TextView courseFormLinksEmpty;
    private TextView courseFormLinksError;
    private final ArrayList<CourseUsefulLinkItem> editingLinks = new ArrayList<>();
    private CreateCourseViewModel viewModel;

    public CreateCourseFragment() {
        super(R.layout.fragment_create_course);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        courseNameLayout = view.findViewById(R.id.courseNameLayout);
        generalInfoLayout = view.findViewById(R.id.generalInfoLayout);
        courseFormLinksList = view.findViewById(R.id.courseFormLinksList);
        courseFormLinksEmpty = view.findViewById(R.id.courseFormLinksEmpty);
        courseFormLinksError = view.findViewById(R.id.courseFormLinksError);

        viewModel = new ViewModelProvider(
                this,
                new CreateCourseViewModelFactory(requireContext().getApplicationContext())
        ).get(CreateCourseViewModel.class);

        view.findViewById(R.id.btnAddCourseLink).setOnClickListener(v -> showAddLinkDialog());
        view.findViewById(R.id.btnCreate).setOnClickListener(v -> submit());

        viewModel.getCreateResult().observe(getViewLifecycleOwner(), this::onCreateResult);

        rebuildLinksList();
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
        String name = courseNameLayout.getEditText() != null
                ? courseNameLayout.getEditText().getText().toString()
                : "";
        String generalInfo = generalInfoLayout.getEditText() != null
                ? generalInfoLayout.getEditText().getText().toString()
                : "";

        courseNameLayout.setError(null);
        generalInfoLayout.setError(null);
        clearLinksError();

        String nameTrimmed = name.trim();
        String generalInfoTrimmed = generalInfo.trim();

        int maxCourseName = getResources().getInteger(R.integer.max_course_name_length);
        int maxGeneralInfo = getResources().getInteger(R.integer.max_general_info_length);

        if (nameTrimmed.isEmpty()) {
            courseNameLayout.setError(getString(R.string.enter_course_name));
            return;
        }
        if (generalInfoTrimmed.isEmpty()) {
            generalInfoLayout.setError(getString(R.string.enter_general_info));
            return;
        }
        int linksErr = CourseUsefulLinksForm.validateForSubmit(editingLinks);
        if (linksErr != 0) {
            setLinksError(getString(linksErr));
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

        viewModel.createCourse(nameTrimmed, generalInfoTrimmed, new ArrayList<>(editingLinks));
    }

    private void onCreateResult(@Nullable Result<Void> result) {
        if (result == null) {
            courseNameLayout.setError(null);
            generalInfoLayout.setError(null);
            return;
        }

        if (result instanceof Result.Success) {
            courseNameLayout.setError(null);
            generalInfoLayout.setError(null);
            clearLinksError();
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
