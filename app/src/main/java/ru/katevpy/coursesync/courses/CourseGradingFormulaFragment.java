package ru.katevpy.coursesync.courses;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import ru.katevpy.coursesync.ui.ErrorUi;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CourseGradingElementItem;
import ru.katevpy.coursesync.shared.dto.GroupDetailsResponse;
import ru.katevpy.coursesync.shared.repository.GroupRepository;
import ru.katevpy.coursesync.shared.dto.CourseGradingElementsResponse;
import ru.katevpy.coursesync.shared.dto.CourseGradingTextResponse;
import ru.katevpy.coursesync.shared.util.Result;

public class CourseGradingFormulaFragment extends Fragment {

    private CourseGradingFormulaViewModel viewModel;
    private UUID courseId;
    private ImageButton btnGradingMore;
    private LinearLayout gradingElementsRows;
    private LinearLayout gradingScoresContainer;
    private MaterialButton btnEditGradingFormula;

    public CourseGradingFormulaFragment() {
        super(R.layout.fragment_course_grading_formula);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnGradingMore = view.findViewById(R.id.btnGradingMore);
        gradingElementsRows = view.findViewById(R.id.gradingElementsRows);
        gradingScoresContainer = view.findViewById(R.id.gradingScoresContainer);

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

        viewModel = new ViewModelProvider(this, new CourseGradingFormulaViewModelFactory())
                .get(CourseGradingFormulaViewModel.class);

        viewModel.getGradingTextResult().observe(getViewLifecycleOwner(), this::onGradingTextResult);
        viewModel.getGradingElementsResult().observe(getViewLifecycleOwner(), this::onGradingElementsResult);
        viewModel.getLoadInProgress().observe(getViewLifecycleOwner(), busy -> {
            if (btnGradingMore != null) {
                btnGradingMore.setEnabled(busy == null || !busy);
            }
        });

        btnGradingMore.setOnClickListener(v -> viewModel.loadGradingText(courseId));

        btnEditGradingFormula = view.findViewById(R.id.btnEditGradingFormula);
        btnEditGradingFormula.setVisibility(View.GONE);
        btnEditGradingFormula.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("courseId", idStr);
            NavHostFragment.findNavController(CourseGradingFormulaFragment.this)
                    .navigate(R.id.action_courseGradingFormulaFragment_to_editCourseGradingFormulaFragment, args);
        });
        refreshEditGradingButtonVisibility();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (courseId != null && viewModel != null) {
            viewModel.loadGradingElements(courseId);
        }
        refreshEditGradingButtonVisibility();
    }

    private void refreshEditGradingButtonVisibility() {
        if (btnEditGradingFormula == null) {
            return;
        }
        new Thread(() -> {
            Result<GroupDetailsResponse> result =
                    new GroupRepository(App.getDeps().groupApi).getCurrentGroup();
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || btnEditGradingFormula == null) {
                    return;
                }
                boolean owner = false;
                if (result instanceof Result.Success) {
                    GroupDetailsResponse data = ((Result.Success<GroupDetailsResponse>) result).data;
                    owner = data != null && data.role != null
                            && "owner".equalsIgnoreCase(data.role.trim());
                }
                btnEditGradingFormula.setVisibility(owner ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    private void onGradingElementsResult(@Nullable Result<CourseGradingElementsResponse> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
            CourseGradingElementsResponse body = ((Result.Success<CourseGradingElementsResponse>) result).data;
            List<CourseGradingElementItem> items =
                    body != null && body.elements != null ? body.elements : Collections.emptyList();
            renderGradingElements(items);
            renderAccumulatedScores(body);
            return;
        }
        clearAccumulatedScores();
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

    private void clearAccumulatedScores() {
        if (gradingScoresContainer != null) {
            gradingScoresContainer.removeAllViews();
        }
    }

    private void renderAccumulatedScores(@Nullable CourseGradingElementsResponse data) {
        if (gradingScoresContainer == null) {
            return;
        }
        gradingScoresContainer.removeAllViews();
        if (data == null) {
            return;
        }
        android.content.res.Resources res = getResources();
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.getDefault());
        int rowGap = res.getDimensionPixelSize(R.dimen.grid_1);

        TextView overall = new TextView(requireContext());
        overall.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        overall.setTextAppearance(R.style.TextAppearance_CourseSync_Body);
        overall.setText(getString(R.string.grading_overall_average, formatScoreDisplay(data.averageGrade, nf)));
        LinearLayout.LayoutParams overallLp = (LinearLayout.LayoutParams) overall.getLayoutParams();
        overallLp.bottomMargin = rowGap;
        overall.setLayoutParams(overallLp);
        gradingScoresContainer.addView(overall);

        List<CourseGradingElementItem> elements = data.elements;
        if (elements == null || elements.isEmpty()) {
            return;
        }
        for (CourseGradingElementItem item : elements) {
            int count = item != null && item.count != null ? item.count : 0;
            String name = item != null && item.name != null ? item.name : "";
            String avgDisplay = formatScoreDisplay(item != null ? item.averageScore : null, nf);

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = rowGap;
            row.setLayoutParams(rowLp);
            TypedValue selectable = new TypedValue();
            requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectable, true);
            row.setBackgroundResource(selectable.resourceId);
            row.setClickable(true);
            row.setFocusable(true);

            TextView left = new TextView(requireContext());
            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            leftLp.setMarginEnd(res.getDimensionPixelSize(R.dimen.grid_1));
            left.setLayoutParams(leftLp);
            left.setTextAppearance(R.style.TextAppearance_CourseSync_Body);
            left.setText(getString(R.string.grading_element_score_line_left, name, count));

            LinearLayout scoreWrap = new LinearLayout(requireContext());
            scoreWrap.setOrientation(LinearLayout.HORIZONTAL);
            scoreWrap.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams scoreWrapLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            scoreWrapLp.setMarginEnd(res.getDimensionPixelSize(R.dimen.grid_1));
            scoreWrap.setLayoutParams(scoreWrapLp);

            int lockPx = res.getDimensionPixelSize(R.dimen.grading_lock_icon_size);
            ImageView lockBelow = new ImageView(requireContext());
            lockBelow.setLayoutParams(new LinearLayout.LayoutParams(lockPx, lockPx));
            lockBelow.setImageResource(R.drawable.ic_lock_closed_small);
            lockBelow.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(
                    requireContext(), android.R.color.holo_red_dark)));
            lockBelow.setScaleType(ImageView.ScaleType.FIT_CENTER);
            lockBelow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            boolean belowThreshold = isAverageBelowBlockThreshold(item);
            lockBelow.setVisibility(belowThreshold ? View.VISIBLE : View.GONE);
            LinearLayout.LayoutParams lockLp = (LinearLayout.LayoutParams) lockBelow.getLayoutParams();
            lockLp.setMarginEnd(belowThreshold ? res.getDimensionPixelSize(R.dimen.grid_1) : 0);
            lockBelow.setLayoutParams(lockLp);

            TextView right = new TextView(requireContext());
            right.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            right.setTextAppearance(R.style.TextAppearance_CourseSync_Body);
            right.setText(avgDisplay);

            scoreWrap.addView(lockBelow);
            scoreWrap.addView(right);

            int iconPx = res.getDimensionPixelSize(R.dimen.grid_3);
            ImageView chevron = new ImageView(requireContext());
            chevron.setLayoutParams(new LinearLayout.LayoutParams(iconPx, iconPx));
            chevron.setImageResource(R.drawable.ic_chevron_right);
            chevron.setScaleType(ImageView.ScaleType.FIT_CENTER);
            chevron.setContentDescription(getString(R.string.grading_row_open_scores_hint));
            chevron.setClickable(false);
            chevron.setFocusable(false);

            row.addView(left);
            row.addView(scoreWrap);
            row.addView(chevron);

            row.setOnClickListener(v -> {
                if (courseId == null || name.isEmpty()) {
                    return;
                }
                Bundle args = new Bundle();
                args.putString(GradingElementScoresFragment.ARG_COURSE_ID, courseId.toString());
                args.putString(GradingElementScoresFragment.ARG_ELEMENT_NAME, name);
                args.putString(GradingElementScoresFragment.ARG_AVERAGE_SCORE_DISPLAY, avgDisplay);
                NavHostFragment.findNavController(CourseGradingFormulaFragment.this)
                        .navigate(R.id.action_courseGradingFormulaFragment_to_gradingElementScoresFragment, args);
            });

            gradingScoresContainer.addView(row);
        }
    }

    private static String formatScoreDisplay(@Nullable Double value, NumberFormat nf) {
        if (value == null) {
            return "—";
        }
        return nf.format(value);
    }

    private static boolean isAverageBelowBlockThreshold(@Nullable CourseGradingElementItem item) {
        if (item == null) {
            return false;
        }
        Double block = item.blockGrade;
        double blockVal = block != null ? block : 0.0;
        if (blockVal <= 0.0) {
            return false;
        }
        Double avg = item.averageScore;
        if (avg == null) {
            return false;
        }
        return avg < blockVal;
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
        for (CourseGradingElementItem item : items) {
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

            LinearLayout blockCol = createBlockColumn(item != null ? item.blockGrade : null);

            row.addView(nameCol);
            row.addView(coefCol);
            row.addView(blockCol);
            gradingElementsRows.addView(row);
        }
    }

    @NonNull
    private LinearLayout createBlockColumn(@Nullable Double blockGradeRaw) {
        double blockGrade = blockGradeRaw != null ? blockGradeRaw : 0.0;
        if (blockGrade < 0.0) {
            blockGrade = 0.0;
        }
        LinearLayout blockCol = new LinearLayout(requireContext());
        blockCol.setOrientation(LinearLayout.HORIZONTAL);
        blockCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        blockCol.setGravity(android.view.Gravity.CENTER_VERTICAL);

        boolean unlocked = Math.abs(blockGrade) < 0.0001;
        ImageView lock = new ImageView(requireContext());
        int iconSize = getResources().getDimensionPixelSize(R.dimen.grading_lock_icon_size);
        LinearLayout.LayoutParams lockLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        lock.setLayoutParams(lockLp);
        lock.setImageResource(unlocked ? R.drawable.ic_lock_open_small : R.drawable.ic_lock_closed_small);
        lock.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(
                requireContext(),
                unlocked ? android.R.color.holo_green_dark : android.R.color.holo_red_dark)));
        lock.setContentDescription(null);
        blockCol.addView(lock);

        if (!unlocked) {
            TextView value = new TextView(requireContext());
            LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            valueLp.setMarginStart(getResources().getDimensionPixelSize(R.dimen.grid_1));
            value.setLayoutParams(valueLp);
            value.setTextAppearance(R.style.TextAppearance_CourseSync_Body);
            NumberFormat nf = NumberFormat.getNumberInstance(Locale.getDefault());
            value.setText(nf.format(blockGrade));
            value.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
            blockCol.addView(value);
        }
        return blockCol;
    }

    private static String formatCoefficient(@Nullable Double c, NumberFormat nf) {
        if (c == null) {
            return "—";
        }
        return nf.format(c);
    }

    private void onGradingTextResult(@Nullable Result<CourseGradingTextResponse> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
            CourseGradingTextResponse data = ((Result.Success<CourseGradingTextResponse>) result).data;
            String raw = data != null ? data.text : null;
            String display;
            if (raw == null || raw.trim().isEmpty()) {
                display = getString(R.string.grading_formula_text_empty);
            } else {
                display = raw;
            }
            showGradingTextDialog(display);
            viewModel.clearGradingTextResult();
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<CourseGradingTextResponse>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                viewModel.clearGradingTextResult();
                return;
            }
            if (code == 403 || code == 404 || code == 400) {
                ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
                NavHostFragment.findNavController(this).navigateUp();
                viewModel.clearGradingTextResult();
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.grading_formula_load_error, ErrorUi.Duration.SHORT);
                viewModel.clearGradingTextResult();
                return;
            }
        }
        if (result instanceof Result.NetworkError) {
            ErrorUi.show(this, R.string.network_error, ErrorUi.Duration.SHORT);
            viewModel.clearGradingTextResult();
            return;
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
        viewModel.clearGradingTextResult();
    }

    private void showGradingTextDialog(@NonNull String text) {
        View dView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_grading_text, null);
        TextView tv = dView.findViewById(R.id.tvGradingTextContent);
        ImageButton close = dView.findViewById(R.id.btnCloseGradingText);
        tv.setText(text);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dView)
                .create();
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
