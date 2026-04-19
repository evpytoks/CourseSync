package ru.katevpy.coursesync.courses;

import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
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
        if (!syncAllRowsFromUi()) {
            ErrorUi.show(this, R.string.grading_fix_element_table_errors, ErrorUi.Duration.SHORT);
            return;
        }
        if (!hasValidCoefficientSum(editableElements)) {
            ErrorUi.show(this, R.string.grading_coeff_sum_must_be_one, ErrorUi.Duration.SHORT);
            return;
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
            if (coefficient == null || coefficient <= 0.0 || coefficient > 1.0) {
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
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < items.size(); i++) {
            CourseGradingElementItem item = items.get(i);
            final int rowIndex = i;
            View row = inflater.inflate(R.layout.item_edit_grading_element_row, gradingElementsRows, false);
            LinearLayout.LayoutParams rowLp = (LinearLayout.LayoutParams) row.getLayoutParams();
            if (rowLp == null) {
                rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            rowLp.bottomMargin = rowSpacing;
            row.setLayoutParams(rowLp);

            TextView nameView = row.findViewById(R.id.gradingRowName);
            TextInputLayout coefLayout = row.findViewById(R.id.gradingRowCoefLayout);
            TextInputEditText coefInput = row.findViewById(R.id.gradingRowCoefInput);
            TextInputLayout blockLayout = row.findViewById(R.id.gradingRowBlockLayout);
            TextInputEditText blockInput = row.findViewById(R.id.gradingRowBlockInput);
            MaterialButton deleteBtn = row.findViewById(R.id.gradingRowDelete);

            nameView.setText(item != null && item.name != null ? item.name : "");

            final boolean[] syncing = {false};
            RowViews refs = new RowViews(coefLayout, coefInput, blockLayout, blockInput);
            row.setTag(refs);

            syncing[0] = true;
            coefLayout.setError(null);
            blockLayout.setError(null);
            coefInput.setText(formatCoefficient(item != null ? item.coefficient : null, nf));
            blockInput.setText(formatBlockGradeForField(item != null ? item.blockGrade : null, nf));
            syncing[0] = false;

            coefInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (syncing[0] || item == null) {
                        return;
                    }
                    refreshCoefFromField(coefLayout, s != null ? s.toString() : "", item);
                }
            });
            blockInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (syncing[0] || item == null) {
                        return;
                    }
                    refreshBlockFromField(blockLayout, s != null ? s.toString() : "", item);
                }
            });

            deleteBtn.setOnClickListener(v -> {
                if (rowIndex < 0 || rowIndex >= items.size()) {
                    return;
                }
                items.remove(rowIndex);
                renderGradingElements(items);
            });

            gradingElementsRows.addView(row);
        }
    }

    private boolean syncAllRowsFromUi() {
        if (gradingElementsRows == null) {
            return true;
        }
        boolean allOk = true;
        int n = gradingElementsRows.getChildCount();
        for (int i = 0; i < n; i++) {
            View row = gradingElementsRows.getChildAt(i);
            Object tag = row.getTag();
            if (!(tag instanceof RowViews) || i >= editableElements.size()) {
                continue;
            }
            CourseGradingElementItem item = editableElements.get(i);
            if (!applyRowFieldsToItem((RowViews) tag, item)) {
                allOk = false;
            }
        }
        return allOk;
    }

    private boolean applyRowFieldsToItem(@NonNull RowViews rv, @NonNull CourseGradingElementItem item) {
        rv.coefLayout.setError(null);
        rv.blockLayout.setError(null);
        String coefRaw = rv.coefInput.getText() != null ? rv.coefInput.getText().toString().trim() : "";
        if (coefRaw.isEmpty()) {
            rv.coefLayout.setError(getString(R.string.grading_invalid_element_coefficient));
            item.coefficient = null;
            return false;
        }
        double c;
        try {
            c = Double.parseDouble(coefRaw.replace(',', '.'));
        } catch (NumberFormatException e) {
            rv.coefLayout.setError(getString(R.string.grading_invalid_element_coefficient));
            item.coefficient = null;
            return false;
        }
        if (c <= 0.0 || c > 1.0) {
            rv.coefLayout.setError(getString(R.string.grading_invalid_element_coefficient));
            item.coefficient = null;
            return false;
        }
        item.coefficient = c;

        String blockRaw = rv.blockInput.getText() != null ? rv.blockInput.getText().toString().trim() : "";
        double b;
        if (blockRaw.isEmpty()) {
            b = 0.0;
        } else {
            try {
                b = Double.parseDouble(blockRaw.replace(',', '.'));
            } catch (NumberFormatException e) {
                rv.blockLayout.setError(getString(R.string.grading_block_invalid_range));
                return false;
            }
        }
        if (b < 0.0 || b > 10.0) {
            rv.blockLayout.setError(getString(R.string.grading_block_invalid_range));
            return false;
        }
        item.blockGrade = b;
        return true;
    }

    private void refreshCoefFromField(
            @NonNull TextInputLayout layout,
            @NonNull String rawTrimmed,
            @NonNull CourseGradingElementItem item) {
        if (rawTrimmed.isEmpty()) {
            layout.setError(getString(R.string.grading_invalid_element_coefficient));
            item.coefficient = null;
            return;
        }
        double c;
        try {
            c = Double.parseDouble(rawTrimmed.replace(',', '.'));
        } catch (NumberFormatException e) {
            layout.setError(getString(R.string.grading_invalid_element_coefficient));
            item.coefficient = null;
            return;
        }
        if (c <= 0.0 || c > 1.0) {
            layout.setError(getString(R.string.grading_invalid_element_coefficient));
            item.coefficient = null;
            return;
        }
        layout.setError(null);
        item.coefficient = c;
    }

    private void refreshBlockFromField(
            @NonNull TextInputLayout layout,
            @NonNull String rawTrimmed,
            @NonNull CourseGradingElementItem item) {
        if (rawTrimmed.isEmpty()) {
            layout.setError(null);
            item.blockGrade = 0.0;
            return;
        }
        double b;
        try {
            b = Double.parseDouble(rawTrimmed.replace(',', '.'));
        } catch (NumberFormatException e) {
            layout.setError(getString(R.string.grading_block_invalid_range));
            return;
        }
        if (b < 0.0 || b > 10.0) {
            layout.setError(getString(R.string.grading_block_invalid_range));
            return;
        }
        layout.setError(null);
        item.blockGrade = b;
    }

    private static final class RowViews {
        final TextInputLayout coefLayout;
        final TextInputEditText coefInput;
        final TextInputLayout blockLayout;
        final TextInputEditText blockInput;

        RowViews(
                TextInputLayout coefLayout,
                TextInputEditText coefInput,
                TextInputLayout blockLayout,
                TextInputEditText blockInput) {
            this.coefLayout = coefLayout;
            this.coefInput = coefInput;
            this.blockLayout = blockLayout;
            this.blockInput = blockInput;
        }
    }

    @NonNull
    private static String formatBlockGradeForField(@Nullable Double value, @NonNull NumberFormat nf) {
        if (value == null) {
            return nf.format(0.0);
        }
        return nf.format(value);
    }

    private static String formatCoefficient(@Nullable Double c, NumberFormat nf) {
        if (c == null) {
            return "";
        }
        return nf.format(c);
    }
}
