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
import ru.katevpy.coursesync.shared.dto.CourseContactMethodItem;
import ru.katevpy.coursesync.shared.dto.CourseContactPersonItem;
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
    private LinearLayout courseFormContactsList;
    private TextView courseFormContactsEmpty;
    private TextView courseFormContactsError;
    private final ArrayList<CourseUsefulLinkItem> editingLinks = new ArrayList<>();
    private final ArrayList<EditableContactPerson> editingContacts = new ArrayList<>();
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
        courseFormContactsList = view.findViewById(R.id.courseFormContactsList);
        courseFormContactsEmpty = view.findViewById(R.id.courseFormContactsEmpty);
        courseFormContactsError = view.findViewById(R.id.courseFormContactsError);

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
        view.findViewById(R.id.btnAddCourseTeacher).setOnClickListener(v -> showAddTeacherDialog());
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
        editingContacts.clear();
        if (data.contacts != null) {
            for (CourseContactPersonItem person : data.contacts) {
                if (person == null) {
                    continue;
                }
                String personName = person.name != null ? person.name.trim() : "";
                if (personName.isEmpty() || person.contactMethods == null) {
                    continue;
                }
                EditableContactPerson editable = new EditableContactPerson(personName);
                for (CourseContactMethodItem method : person.contactMethods) {
                    if (method == null) {
                        continue;
                    }
                    String type = CourseContactsForm.trim(method.type);
                    String value = CourseContactsForm.trim(method.value);
                    if (type.isEmpty() || value.isEmpty()) {
                        continue;
                    }
                    editable.methods.add(new CourseContactMethodItem(type, value));
                }
                editingContacts.add(editable);
            }
        }
        rebuildContactsList();
        clearLinksError();
        clearContactsError();
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

    private void rebuildContactsList() {
        courseFormContactsList.removeAllViews();
        if (editingContacts.isEmpty()) {
            courseFormContactsEmpty.setVisibility(View.VISIBLE);
            return;
        }
        courseFormContactsEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < editingContacts.size(); i++) {
            EditableContactPerson person = editingContacts.get(i);
            final int personIndex = i;
            View personRow = inflater.inflate(R.layout.item_course_contact_person_edit_row, courseFormContactsList, false);
            TextView personNameView = personRow.findViewById(R.id.editCourseContactPersonName);
            personNameView.setText(person.name != null ? person.name : "");
            personRow.findViewById(R.id.editCourseContactPersonRemove).setOnClickListener(v -> {
                editingContacts.remove(personIndex);
                rebuildContactsList();
                clearContactsError();
            });
            personRow.findViewById(R.id.editCourseContactAddMethod).setOnClickListener(v -> showAddMethodDialog(personIndex));
            LinearLayout methodsList = personRow.findViewById(R.id.editCourseContactMethodsList);
            for (int j = 0; j < person.methods.size(); j++) {
                CourseContactMethodItem method = person.methods.get(j);
                final int methodIndex = j;
                View methodRow = inflater.inflate(R.layout.item_course_contact_method_edit_row, methodsList, false);
                TextView methodTypeView = methodRow.findViewById(R.id.editCourseContactMethodType);
                TextView methodValueView = methodRow.findViewById(R.id.editCourseContactMethodValue);
                String type = CourseContactsForm.trim(method.type);
                String value = CourseContactsForm.trim(method.value);
                methodValueView.setText(value);
                if (type.isEmpty()) {
                    methodTypeView.setVisibility(View.GONE);
                } else {
                    methodTypeView.setVisibility(View.VISIBLE);
                    methodTypeView.setText(type);
                }
                methodRow.findViewById(R.id.editCourseContactMethodRemove).setOnClickListener(v -> {
                    person.methods.remove(methodIndex);
                    rebuildContactsList();
                    clearContactsError();
                });
                methodsList.addView(methodRow);
            }
            courseFormContactsList.addView(personRow);
        }
    }

    private void clearContactsError() {
        courseFormContactsError.setVisibility(View.GONE);
        courseFormContactsError.setText("");
    }

    private void setContactsError(@NonNull String message) {
        courseFormContactsError.setText(message);
        courseFormContactsError.setVisibility(View.VISIBLE);
    }

    private void showAddTeacherDialog() {
        if (!CourseContactsForm.canAddTeacher(editingContacts)) {
            ErrorUi.show(this, R.string.contacts_too_many_people, ErrorUi.Duration.SHORT);
            return;
        }
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_contact_teacher, null, false);
        TextInputLayout personLayout = dialogView.findViewById(R.id.dialogContactPersonLayout);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.add_teacher_dialog_title)
                .setView(dialogView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.add_content, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            personLayout.setError(null);
            String person = personLayout.getEditText() != null ? personLayout.getEditText().getText().toString() : "";

            int ep = CourseContactsForm.validateDraftPersonName(person);
            if (ep != 0) {
                personLayout.setError(getString(ep));
                return;
            }
            editingContacts.add(new EditableContactPerson(person.trim()));
            rebuildContactsList();
            clearContactsError();
            dialog.dismiss();
        }));

        dialog.show();
    }

    private void showAddMethodDialog(int personIndex) {
        if (personIndex < 0 || personIndex >= editingContacts.size()) {
            return;
        }
        EditableContactPerson person = editingContacts.get(personIndex);
        if (!CourseContactsForm.canAddMethod(person)) {
            ErrorUi.show(this, R.string.contacts_too_many_methods, ErrorUi.Duration.SHORT);
            return;
        }
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_contact_method, null, false);
        TextInputLayout typeLayout = dialogView.findViewById(R.id.dialogContactTypeLayout);
        TextInputLayout valueLayout = dialogView.findViewById(R.id.dialogContactValueLayout);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.add_contact_method_dialog_title)
                .setView(dialogView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.add_content, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            typeLayout.setError(null);
            valueLayout.setError(null);
            String type = typeLayout.getEditText() != null ? typeLayout.getEditText().getText().toString() : "";
            String value = valueLayout.getEditText() != null ? valueLayout.getEditText().getText().toString() : "";
            int et = CourseContactsForm.validateDraftType(type);
            if (et != 0) {
                typeLayout.setError(getString(et));
                return;
            }
            int ev = CourseContactsForm.validateDraftValue(value);
            if (ev != 0) {
                valueLayout.setError(getString(ev));
                return;
            }
            person.methods.add(new CourseContactMethodItem(type.trim(), value.trim()));
            rebuildContactsList();
            clearContactsError();
            dialog.dismiss();
        }));
        dialog.show();
    }

    @NonNull
    private static List<CourseContactPersonItem> buildContactsPayload(@NonNull List<EditableContactPerson> source) {
        List<CourseContactPersonItem> out = new ArrayList<>();
        for (EditableContactPerson person : source) {
            if (person == null) {
                continue;
            }
            String name = CourseContactsForm.trim(person.name);
            if (name.isEmpty()) {
                continue;
            }
            out.add(new CourseContactPersonItem(name, new ArrayList<>(person.methods)));
        }
        return out;
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
        clearContactsError();

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
        int contactsErr = CourseContactsForm.validateForSubmit(editingContacts);
        if (contactsErr != 0) {
            setContactsError(getString(contactsErr));
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

        viewModel.updateCourse(
                courseId,
                nameTrimmed,
                generalTrimmed,
                buildContactsPayload(editingContacts),
                new ArrayList<>(editingLinks));
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
