package ru.katevpy.coursesync.courses;

import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CourseGradingElementItem;
import ru.katevpy.coursesync.shared.dto.CourseGradingElementsResponse;
import ru.katevpy.coursesync.shared.dto.CourseGradingTextResponse;
import ru.katevpy.coursesync.shared.util.Result;
import ru.katevpy.coursesync.ui.ErrorUi;

public class EditCourseGradingFormulaFragment extends Fragment {

    private TextInputLayout descriptionLayout;
    private LinearLayout gradingElementsRows;
    private EditCourseGradingFormulaViewModel viewModel;
    private UUID courseId;
    private final List<CourseGradingElementItem> editableElements = new ArrayList<>();

    public EditCourseGradingFormulaFragment() {
        super(R.layout.fragment_edit_course_grading_formula);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        descriptionLayout = view.findViewById(R.id.editGradingDescriptionLayout);
        gradingElementsRows = view.findViewById(R.id.editGradingElementsRows);

        if (getArguments() == null) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        String idStr = getArguments().getString("courseId");
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

        viewModel = new ViewModelProvider(this, new EditCourseGradingFormulaViewModelFactory())
                .get(EditCourseGradingFormulaViewModel.class);
        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);
        viewModel.getGradingElementsResult().observe(getViewLifecycleOwner(), this::onGradingElementsResult);
        viewModel.getSaveResult().observe(getViewLifecycleOwner(), this::onSaveResult);

        view.findViewById(R.id.btnAddControlElement).setOnClickListener(v -> showAddElementDialog());
        view.findViewById(R.id.btnSaveGradingFormula).setOnClickListener(v -> submit());

        viewModel.loadGradingText(courseId);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (courseId != null && viewModel != null) {
            viewModel.loadGradingElements(courseId);
        }
    }

    private void onLoadResult(@Nullable Result<CourseGradingTextResponse> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
            CourseGradingTextResponse data = ((Result.Success<CourseGradingTextResponse>) result).data;
            String t = data != null && data.text != null ? data.text : "";
            if (descriptionLayout.getEditText() != null) {
                descriptionLayout.getEditText().setText(t);
            }
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<CourseGradingTextResponse>) result).httpCode;
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
                ErrorUi.show(this, R.string.grading_formula_load_error, ErrorUi.Duration.SHORT);
                return;
            }
        }
        if (result instanceof Result.NetworkError) {
            ErrorUi.show(this, R.string.network_error, ErrorUi.Duration.SHORT);
            return;
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
    }

    private void onGradingElementsResult(@Nullable Result<CourseGradingElementsResponse> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
            CourseGradingElementsResponse body = ((Result.Success<CourseGradingElementsResponse>) result).data;
            editableElements.clear();
            if (body != null && body.elements != null) {
                editableElements.addAll(body.elements);
            }
            renderGradingElements(editableElements);
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<CourseGradingElementsResponse>) result).httpCode;
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
                ErrorUi.show(this, R.string.grading_elements_load_error, ErrorUi.Duration.SHORT);
                return;
            }
        }
        if (result instanceof Result.NetworkError) {
            ErrorUi.show(this, R.string.network_error, ErrorUi.Duration.SHORT);
            return;
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
    }

    private void submit() {
        if (courseId == null || viewModel == null || descriptionLayout == null || descriptionLayout.getEditText() == null) {
            return;
        }
        if (!hasValidCoefficientSum(editableElements)) {
            ErrorUi.show(this, R.string.grading_coeff_sum_must_be_one, ErrorUi.Duration.SHORT);
            return;
        }
        for (CourseGradingElementItem item : editableElements) {
            double b = item != null && item.blockGrade != null ? item.blockGrade : 0.0;
            if (b < 0.0 || b > 10.0) {
                ErrorUi.show(this, R.string.grading_block_invalid_range, ErrorUi.Duration.SHORT);
                return;
            }
        }
        String text = descriptionLayout.getEditText().getText().toString();
        viewModel.saveGrading(courseId, text, editableElements);
    }

    private static boolean hasValidCoefficientSum(@Nullable List<CourseGradingElementItem> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        double sum = 0.0;
        for (CourseGradingElementItem item : items) {
            if (item == null || item.coefficient == null) {
                return false;
            }
            sum += item.coefficient;
        }
        return Math.abs(sum - 1.0) <= 0.0001;
    }

    private void showAddElementDialog() {
        if (!isAdded()) return;

        android.content.res.Resources res = getResources();
        int pad = res.getDimensionPixelSize(R.dimen.card_content_padding);
        int fieldGap = res.getDimensionPixelSize(R.dimen.grid_1);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, 0);

        TextInputLayout nameLayout = new TextInputLayout(requireContext());
        TextInputEditText nameInput = new TextInputEditText(requireContext());
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nameLayout.setHint(getString(R.string.grading_element_name_hint));
        nameLayout.addView(nameInput);
        root.addView(nameLayout);

        TextInputLayout coefLayout = new TextInputLayout(requireContext());
        LinearLayout.LayoutParams coefLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        coefLp.topMargin = fieldGap;
        coefLayout.setLayoutParams(coefLp);
        TextInputEditText coefInput = new TextInputEditText(requireContext());
        coefInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        coefLayout.setHint(getString(R.string.grading_element_coefficient_hint));
        coefLayout.addView(coefInput);
        root.addView(coefLayout);

        TextInputLayout blockLayout = new TextInputLayout(requireContext());
        LinearLayout.LayoutParams blockLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        blockLp.topMargin = fieldGap;
        blockLayout.setLayoutParams(blockLp);
        TextInputEditText blockInput = new TextInputEditText(requireContext());
        blockInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        blockLayout.setHint(getString(R.string.grading_element_block_hint));
        blockLayout.addView(blockInput);
        root.addView(blockLayout);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.grading_add_element_title)
                .setView(root)
                .setNegativeButton(R.string.event_no, null)
                .setPositiveButton(R.string.event_yes, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            nameLayout.setError(null);
            coefLayout.setError(null);
            blockLayout.setError(null);

            String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
            String coefRaw = coefInput.getText() != null ? coefInput.getText().toString().trim() : "";
            String blockRaw = blockInput.getText() != null ? blockInput.getText().toString().trim() : "";
            if (name.isEmpty()) {
                nameLayout.setError(getString(R.string.grading_invalid_element_name));
                return;
            }

            Double coefficient;
            try {
                coefficient = Double.parseDouble(coefRaw.replace(',', '.'));
            } catch (Exception ignored) {
                coefficient = null;
            }
            if (coefficient == null || coefficient < 0.0 || coefficient > 1.0) {
                coefLayout.setError(getString(R.string.grading_invalid_element_coefficient));
                return;
            }

            Double blockGrade;
            try {
                blockGrade = blockRaw.isEmpty() ? 0.0 : Double.parseDouble(blockRaw.replace(',', '.'));
            } catch (Exception e) {
                blockLayout.setError(getString(R.string.grading_block_invalid_range));
                return;
            }
            if (blockGrade < 0.0 || blockGrade > 10.0) {
                blockLayout.setError(getString(R.string.grading_block_invalid_range));
                return;
            }

            CourseGradingElementItem item = new CourseGradingElementItem();
            item.name = name;
            item.coefficient = coefficient;
            item.blockGrade = blockGrade;
            editableElements.add(item);
            renderGradingElements(editableElements);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void onSaveResult(@Nullable Result<Void> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<Void>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.grading_formula_save_error, ErrorUi.Duration.SHORT);
                return;
            }
        }
        if (result instanceof Result.NetworkError) {
            ErrorUi.show(this, R.string.network_error, ErrorUi.Duration.SHORT);
            return;
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
    }

    private void renderGradingElements(@Nullable List<CourseGradingElementItem> items) {
        if (gradingElementsRows == null) {
            return;
        }
        gradingElementsRows.removeAllViews();
        if (items == null || items.isEmpty()) {
            return;
        }
        android.content.res.Resources res = getResources();
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.getDefault());
        int rowSpacing = res.getDimensionPixelSize(R.dimen.grid_1);
        int colGap = res.getDimensionPixelSize(R.dimen.grid_1);
        for (int i = 0; i < items.size(); i++) {
            CourseGradingElementItem item = items.get(i);
            final int rowIndex = i;
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = rowSpacing;
            row.setLayoutParams(rowLp);

            TextView nameCol = new TextView(requireContext());
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nameLp.setMarginEnd(colGap);
            nameCol.setLayoutParams(nameLp);
            nameCol.setTextAppearance(R.style.TextAppearance_CourseSync_Body);
            nameCol.setText(item.name != null ? item.name : "");

            TextView coefCol = new TextView(requireContext());
            LinearLayout.LayoutParams coefLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            coefLp.setMarginEnd(colGap);
            coefCol.setLayoutParams(coefLp);
            coefCol.setTextAppearance(R.style.TextAppearance_CourseSync_Body);
            coefCol.setText(formatCoefficient(item.coefficient, nf));

            TextView blockCol = new TextView(requireContext());
            LinearLayout.LayoutParams blockColLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            blockColLp.setMarginEnd(colGap);
            blockCol.setLayoutParams(blockColLp);
            blockCol.setTextAppearance(R.style.TextAppearance_CourseSync_Body);
            blockCol.setText(formatBlockGradeDisplay(item != null ? item.blockGrade : null));

            MaterialButton deleteBtn = new MaterialButton(
                    requireContext(),
                    null,
                    com.google.android.material.R.attr.materialIconButtonStyle
            );
            deleteBtn.setIconResource(android.R.drawable.ic_menu_close_clear_cancel);
            deleteBtn.setInsetTop(0);
            deleteBtn.setInsetBottom(0);
            deleteBtn.setContentDescription(getString(R.string.event_delete));
            deleteBtn.setOnClickListener(v -> {
                if (rowIndex < 0 || rowIndex >= items.size()) {
                    return;
                }
                items.remove(rowIndex);
                renderGradingElements(items);
            });

            row.addView(nameCol);
            row.addView(coefCol);
            row.addView(blockCol);
            row.addView(deleteBtn);
            gradingElementsRows.addView(row);
        }
    }

    @NonNull
    private static String formatBlockGradeDisplay(@Nullable Double value) {
        if (value == null) {
            return "0";
        }
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.getDefault());
        return nf.format(value);
    }

    private static String formatCoefficient(@Nullable Double c, NumberFormat nf) {
        if (c == null) {
            return "—";
        }
        return nf.format(c);
    }
}
