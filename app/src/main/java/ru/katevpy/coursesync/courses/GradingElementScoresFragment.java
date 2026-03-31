package ru.katevpy.coursesync.courses;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.ApiError;
import ru.katevpy.coursesync.shared.dto.CourseGradingScoresResponse;
import ru.katevpy.coursesync.shared.util.Result;
import ru.katevpy.coursesync.ui.ErrorUi;

public class GradingElementScoresFragment extends Fragment {

    private static final double SCORE_MIN = 0.0;
    private static final double SCORE_MAX = 10.0;

    public static final String ARG_COURSE_ID = "courseId";
    public static final String ARG_ELEMENT_NAME = "gradingElementName";
    public static final String ARG_AVERAGE_SCORE_DISPLAY = "averageScoreDisplay";

    private GradingElementScoresViewModel viewModel;
    private UUID courseId;
    private String elementName;
    private TextView tvTitle;
    private TextView tvAverage;
    private LinearLayout tableRows;
    private ImageButton btnAdd;
    private ImageButton btnRemove;
    private MaterialButton btnSave;

    private final ArrayList<Double> localScores = new ArrayList<>();
    private final NumberFormat nf = NumberFormat.getNumberInstance(Locale.getDefault());
    private boolean suppressScoreWatchers;
    private String initialAverageDisplay;

    public GradingElementScoresFragment() {
        super(R.layout.fragment_grading_element_scores);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvTitle = view.findViewById(R.id.tvGradingElementTitle);
        tvAverage = view.findViewById(R.id.tvGradingElementAverage);
        tableRows = view.findViewById(R.id.gradingScoresTableRows);
        btnAdd = view.findViewById(R.id.btnAddGradingScoreEntry);
        btnRemove = view.findViewById(R.id.btnRemoveGradingScoreEntry);
        btnSave = view.findViewById(R.id.btnSaveGradingScores);

        if (getArguments() == null) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        String idStr = getArguments().getString(ARG_COURSE_ID);
        elementName = getArguments().getString(ARG_ELEMENT_NAME);
        String averageDisplay = getArguments().getString(ARG_AVERAGE_SCORE_DISPLAY);
        if (idStr == null || idStr.isEmpty() || elementName == null || elementName.isEmpty()) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        try {
            courseId = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        tvTitle.setText(elementName);
        initialAverageDisplay = averageDisplay;
        updateAverageLineFromInitialOrEmpty();

        viewModel = new ViewModelProvider(this, new GradingElementScoresViewModelFactory())
                .get(GradingElementScoresViewModel.class);
        viewModel.getScoresResult().observe(getViewLifecycleOwner(), this::onScoresResult);
        viewModel.getSaveResult().observe(getViewLifecycleOwner(), this::onSaveResult);

        btnAdd.setOnClickListener(v -> {
            localScores.add(0.0);
            renderFromLocal();
        });
        btnRemove.setOnClickListener(v -> {
            if (!localScores.isEmpty()) {
                localScores.remove(localScores.size() - 1);
                renderFromLocal();
            }
        });
        btnSave.setOnClickListener(v -> trySave());

        updateRemoveButtonState();
        viewModel.loadScores(courseId, elementName);
    }

    private void trySave() {
        if (!syncAllScoreFieldsFromEdits()) {
            ErrorUi.show(this, R.string.grading_score_fix_field_error, ErrorUi.Duration.SHORT);
            return;
        }
        if (!validateScoresForSubmit()) {
            return;
        }
        viewModel.saveScores(courseId, elementName, new ArrayList<>(localScores));
    }

    private boolean validateScoresForSubmit() {
        if (localScores.isEmpty()) {
            ErrorUi.show(this, R.string.grading_scores_need_at_least_one, ErrorUi.Duration.SHORT);
            return false;
        }
        for (Double d : localScores) {
            double v = d != null ? d : 0.0;
            if (v < SCORE_MIN || v > SCORE_MAX) {
                ErrorUi.show(this, R.string.grading_scores_invalid_range, ErrorUi.Duration.SHORT);
                return false;
            }
        }
        return true;
    }

    private boolean refreshScoreField(@NonNull TextInputEditText edit, int index) {
        String t = edit.getText() != null ? edit.getText().toString().trim() : "";
        if (t.isEmpty()) {
            edit.setError(null);
            if (index < localScores.size()) {
                localScores.set(index, 0.0);
            }
            return true;
        }
        try {
            double v = Double.parseDouble(t.replace(',', '.'));
            if (index < localScores.size()) {
                localScores.set(index, v);
            }
            if (v < SCORE_MIN || v > SCORE_MAX) {
                edit.setError(getString(R.string.grading_scores_invalid_range));
                return false;
            }
            edit.setError(null);
            return true;
        } catch (NumberFormatException e) {
            edit.setError(getString(R.string.grading_scores_invalid_range));
            return false;
        }
    }

    private boolean syncAllScoreFieldsFromEdits() {
        boolean allOk = true;
        for (int i = 0; i < tableRows.getChildCount(); i++) {
            View rowView = tableRows.getChildAt(i);
            if (!(rowView instanceof LinearLayout)) {
                continue;
            }
            LinearLayout row = (LinearLayout) rowView;
            if (row.getChildCount() < 2) {
                continue;
            }
            View scoreView = row.getChildAt(1);
            if (!(scoreView instanceof TextInputEditText)) {
                continue;
            }
            if (!refreshScoreField((TextInputEditText) scoreView, i)) {
                allOk = false;
            }
        }
        updateAverageDisplay();
        return allOk;
    }

    private void onSaveResult(@Nullable Result<Void> result) {
        if (result == null) {
            return;
        }
        if (result instanceof Result.Success) {
            viewModel.clearSaveResult();
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<Void>) result).httpCode;
            ApiError err = ((Result.HttpError<Void>) result).error;
            if (code == 401) {
                viewModel.clearSaveResult();
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            if (code == 403) {
                ErrorUi.show(this, R.string.grading_scores_save_forbidden, ErrorUi.Duration.SHORT);
                viewModel.clearSaveResult();
                return;
            }
            if (code == 404) {
                ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
                NavHostFragment.findNavController(this).navigateUp();
                viewModel.clearSaveResult();
                return;
            }
            if (code == 400 && err != null && "grading_score_out_of_range".equals(err.code)) {
                ErrorUi.show(this, R.string.grading_scores_invalid_range, ErrorUi.Duration.SHORT);
                viewModel.clearSaveResult();
                return;
            }
            if (code == 400 && err != null && "grading_element_count_invalid".equals(err.code)) {
                ErrorUi.show(this, R.string.grading_scores_need_at_least_one, ErrorUi.Duration.SHORT);
                viewModel.clearSaveResult();
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.grading_scores_save_error, ErrorUi.Duration.SHORT);
                viewModel.clearSaveResult();
                return;
            }
        }
        if (result instanceof Result.NetworkError) {
            ErrorUi.show(this, R.string.network_error, ErrorUi.Duration.SHORT);
            viewModel.clearSaveResult();
            return;
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
        viewModel.clearSaveResult();
    }

    private void onScoresResult(@Nullable Result<CourseGradingScoresResponse> result) {
        if (result == null || tableRows == null) {
            return;
        }
        if (result instanceof Result.Success) {
            CourseGradingScoresResponse data = ((Result.Success<CourseGradingScoresResponse>) result).data;
            localScores.clear();
            if (data != null && data.scores != null) {
                for (Double d : data.scores) {
                    localScores.add(d != null ? d : 0.0);
                }
            }
            renderFromLocal();
            return;
        }
        localScores.clear();
        tableRows.removeAllViews();
        updateRemoveButtonState();
        updateAverageLineFromInitialOrEmpty();
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<CourseGradingScoresResponse>) result).httpCode;
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
                ErrorUi.show(this, R.string.grading_scores_load_error, ErrorUi.Duration.SHORT);
                return;
            }
        }
        if (result instanceof Result.NetworkError) {
            ErrorUi.show(this, R.string.network_error, ErrorUi.Duration.SHORT);
            return;
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
    }

    private void renderFromLocal() {
        tableRows.removeAllViews();
        int gap = getResources().getDimensionPixelSize(R.dimen.grid_1);
        for (int i = 0; i < localScores.size(); i++) {
            final int index = i;
            double val = localScores.get(i);

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = gap;
            row.setLayoutParams(rowLp);

            TextView numCol = new TextView(requireContext());
            numCol.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            numCol.setTextAppearance(R.style.TextAppearance_CourseSync_Body);
            numCol.setText(String.format(Locale.getDefault(), "%d", i + 1));

            TextInputEditText scoreEdit = new TextInputEditText(requireContext());
            scoreEdit.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            scoreEdit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            scoreEdit.setTextAppearance(R.style.TextAppearance_CourseSync_Body);
            setScoreText(scoreEdit, val);
            scoreEdit.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (suppressScoreWatchers) {
                        return;
                    }
                    refreshScoreField(scoreEdit, index);
                    updateAverageDisplay();
                }
            });

            row.addView(numCol);
            row.addView(scoreEdit);
            tableRows.addView(row);
        }
        updateAverageDisplay();
        updateRemoveButtonState();
    }

    private void setScoreText(TextInputEditText edit, double value) {
        suppressScoreWatchers = true;
        edit.setText(formatScoreForEdit(value));
        suppressScoreWatchers = false;
        if (value >= SCORE_MIN && value <= SCORE_MAX) {
            edit.setError(null);
        } else {
            edit.setError(getString(R.string.grading_scores_invalid_range));
        }
    }

    private String formatScoreForEdit(double value) {
        if (value == (long) value) {
            return String.format(Locale.getDefault(), "%d", (long) value);
        }
        return nf.format(value);
    }

    private void updateAverageLineFromInitialOrEmpty() {
        String s = initialAverageDisplay != null && !initialAverageDisplay.isEmpty()
                ? initialAverageDisplay
                : "—";
        tvAverage.setText(getString(R.string.grading_element_scores_average_was, s));
    }

    private void updateAverageDisplay() {
        if (localScores.isEmpty()) {
            updateAverageLineFromInitialOrEmpty();
            return;
        }
        double sum = 0.0;
        for (Double d : localScores) {
            sum += d != null ? d : 0.0;
        }
        double avg = sum / localScores.size();
        tvAverage.setText(getString(R.string.grading_element_scores_average_was, nf.format(avg)));
    }

    private void updateRemoveButtonState() {
        boolean canRemove = !localScores.isEmpty();
        btnRemove.setEnabled(canRemove);
        btnRemove.setAlpha(canRemove ? 1f : 0.38f);
    }
}
