package ru.katevpy.coursesync.courses;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import ru.katevpy.coursesync.ui.ErrorUi;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.ApiError;
import ru.katevpy.coursesync.shared.dto.CourseCumulativeGradeResponse;
import ru.katevpy.coursesync.shared.dto.CourseGradingElementItem;
import ru.katevpy.coursesync.shared.dto.CourseGradingElementListResponse;
import ru.katevpy.coursesync.shared.dto.CourseGradingElementOptionItem;
import ru.katevpy.coursesync.shared.dto.GroupDetailsResponse;
import ru.katevpy.coursesync.shared.repository.CourseRepository;
import ru.katevpy.coursesync.shared.repository.GroupRepository;
import ru.katevpy.coursesync.shared.dto.CourseGradingElementsResponse;
import ru.katevpy.coursesync.shared.dto.CourseGradingTextResponse;
import ru.katevpy.coursesync.shared.dto.CumulativeGradeState;
import ru.katevpy.coursesync.shared.util.Result;

public class CourseGradingFormulaFragment extends Fragment {

    private CourseGradingFormulaViewModel viewModel;
    private UUID courseId;
    private ImageButton btnGradingMore;
    private LinearLayout gradingElementsRows;
    private LinearLayout gradingScoresContainer;
    private MaterialButton btnEditGradingFormula;
    @Nullable
    private CourseGradingElementsResponse lastGradingScoresBody;
    @Nullable
    private CumulativeGradeState lastCumulativeState;
    @Nullable
    private AlertDialog cumulativeEditDialog;
    private boolean isGroupOwnerForCourseActions;

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
        viewModel.getCumulativeGradeResult().observe(getViewLifecycleOwner(), this::onCumulativeGradeResult);
        viewModel.getSaveCumulativeGradeResult().observe(getViewLifecycleOwner(), this::onSaveCumulativeGradeResult);
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

    @Override
    public void onDestroyView() {
        if (cumulativeEditDialog != null && cumulativeEditDialog.isShowing()) {
            cumulativeEditDialog.dismiss();
        }
        cumulativeEditDialog = null;
        super.onDestroyView();
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
                isGroupOwnerForCourseActions = owner;
                btnEditGradingFormula.setVisibility(owner ? View.VISIBLE : View.GONE);
                if (lastGradingScoresBody != null) {
                    renderAccumulatedScores();
                }
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
            lastGradingScoresBody = body;
            lastCumulativeState = null;
            renderAccumulatedScores();
            return;
        }
        lastGradingScoresBody = null;
        lastCumulativeState = null;
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

    private void onCumulativeGradeResult(@Nullable Result<CumulativeGradeState> result) {
        if (result == null) {
            return;
        }
        if (result instanceof Result.Success) {
            lastCumulativeState = ((Result.Success<CumulativeGradeState>) result).data;
            renderAccumulatedScores();
            return;
        }
        lastCumulativeState = new CumulativeGradeState(null, null);
        if (lastGradingScoresBody != null) {
            renderAccumulatedScores();
        }
    }

    private void onSaveCumulativeGradeResult(@Nullable Result<Void> result) {
        if (result == null) {
            return;
        }
        viewModel.clearSaveCumulativeGradeResult();
        if (result instanceof Result.Success) {
            if (cumulativeEditDialog != null && cumulativeEditDialog.isShowing()) {
                cumulativeEditDialog.dismiss();
            }
            cumulativeEditDialog = null;
            if (courseId != null) {
                viewModel.loadGradingElements(courseId);
            }
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<Void>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            ApiError err = ((Result.HttpError<Void>) result).error;
            String ec = err != null ? err.code : null;
            if (code == 403 || "forbidden".equals(ec)) {
                ErrorUi.show(
                        this,
                        cumulativeEditDialog,
                        R.string.grading_cumulative_save_forbidden,
                        ErrorUi.Duration.SHORT);
                return;
            }
            if ("cumulative_grade_block_greater_than_automatic".equals(ec)) {
                ErrorUi.show(
                        this,
                        cumulativeEditDialog,
                        R.string.grading_cumulative_block_gt_auto,
                        ErrorUi.Duration.SHORT);
                return;
            }
            if ("cumulative_grade_elements_required".equals(ec)) {
                ErrorUi.show(
                        this,
                        cumulativeEditDialog,
                        R.string.grading_cumulative_need_element,
                        ErrorUi.Duration.SHORT);
                return;
            }
            if ("cumulative_grade_threshold_out_of_range".equals(ec)) {
                ErrorUi.show(
                        this,
                        cumulativeEditDialog,
                        R.string.grading_cumulative_threshold_range,
                        ErrorUi.Duration.SHORT);
                return;
            }
        }
        if (result instanceof Result.NetworkError) {
            ErrorUi.show(this, cumulativeEditDialog, R.string.network_error, ErrorUi.Duration.SHORT);
            return;
        }
        ErrorUi.show(this, cumulativeEditDialog, R.string.grading_cumulative_save_error, ErrorUi.Duration.SHORT);
    }

    private void clearAccumulatedScores() {
        if (gradingScoresContainer != null) {
            gradingScoresContainer.removeAllViews();
        }
    }

    @NonNull
    private String nakopValueText(@Nullable CumulativeGradeState state, NumberFormat nf) {
        if (state == null || state.configured == null) {
            return "—";
        }
        if (!state.configured) {
            return getString(R.string.grading_nakop_not_configured_yet);
        }
        CourseCumulativeGradeResponse r = state.response;
        return formatScoreDisplay(r != null ? r.value : null, nf);
    }

    @Nullable
    private static CourseCumulativeGradeResponse configuredCumulativeResponse(
            @Nullable CumulativeGradeState state) {
        if (state == null || !Boolean.TRUE.equals(state.configured)) {
            return null;
        }
        return state.response;
    }

    private static boolean cumulativeNeedsBlockLock(@Nullable CourseCumulativeGradeResponse r) {
        if (r == null) {
            return false;
        }
        if (Boolean.FALSE.equals(r.isBlocked)) {
            return false;
        }
        if (Boolean.TRUE.equals(r.isBlocked)) {
            return true;
        }
        double block = r.blockGrade != null ? r.blockGrade : 0.0;
        if (block <= 0.0) {
            return false;
        }
        Double v = r.value;
        return v != null && v < block;
    }

    private static boolean cumulativeAutoThresholdMet(@Nullable CourseCumulativeGradeResponse r) {
        if (r == null || r.automatic == null) {
            return false;
        }
        if (Boolean.FALSE.equals(r.isAuto)) {
            return false;
        }
        if (Boolean.TRUE.equals(r.isAuto)) {
            return true;
        }
        Double v = r.value;
        Double a = r.automatic;
        return v != null && a != null && v >= a;
    }

    private void renderAccumulatedScores() {
        if (gradingScoresContainer == null) {
            return;
        }
        gradingScoresContainer.removeAllViews();
        CourseGradingElementsResponse data = lastGradingScoresBody;
        if (data == null) {
            return;
        }
        android.content.res.Resources res = getResources();
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.getDefault());
        int rowGap = res.getDimensionPixelSize(R.dimen.grid_1);

        int iconPx = res.getDimensionPixelSize(R.dimen.grid_3);
        int lockPx = res.getDimensionPixelSize(R.dimen.grading_lock_icon_size);
        int gap1 = res.getDimensionPixelSize(R.dimen.grid_1);
        int green = ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark);

        LinearLayout nakopRow = new LinearLayout(requireContext());
        nakopRow.setOrientation(LinearLayout.HORIZONTAL);
        nakopRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams nakopRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nakopRowLp.bottomMargin = rowGap;
        nakopRow.setLayoutParams(nakopRowLp);

        CourseCumulativeGradeResponse cum = configuredCumulativeResponse(lastCumulativeState);
        boolean nakopBlockLock = cumulativeNeedsBlockLock(cum);
        boolean nakopAutoOk = cumulativeAutoThresholdMet(cum) && !nakopBlockLock;

        LinearLayout nakopLeftCol = new LinearLayout(requireContext());
        nakopLeftCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nakopLeftColLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nakopLeftColLp.setMarginEnd(gap1);
        nakopLeftCol.setLayoutParams(nakopLeftColLp);

        TextView nakopLabel = new TextView(requireContext());
        nakopLabel.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        nakopLabel.setTextAppearance(R.style.TextAppearance_CourseSync_Body);
        nakopLabel.setText(R.string.grading_nakop_label);
        nakopLeftCol.addView(nakopLabel);

        if (nakopAutoOk) {
            TextView tvAutoHint = new TextView(requireContext());
            LinearLayout.LayoutParams hintLp2 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            hintLp2.topMargin = res.getDimensionPixelSize(R.dimen.grid_1) / 2;
            tvAutoHint.setLayoutParams(hintLp2);
            tvAutoHint.setMaxLines(3);
            tvAutoHint.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvAutoHint.setTextAppearance(R.style.TextAppearance_CourseSync_Body);
            tvAutoHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            tvAutoHint.setTextColor(green);
            tvAutoHint.setText(R.string.grading_cumulative_read_auto_conditions);
            nakopLeftCol.addView(tvAutoHint);
        }

        LinearLayout nakopScoreWrap = new LinearLayout(requireContext());
        nakopScoreWrap.setOrientation(LinearLayout.HORIZONTAL);
        nakopScoreWrap.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams nakopScoreLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nakopScoreLp.setMarginEnd(gap1);
        nakopScoreWrap.setLayoutParams(nakopScoreLp);

        if (nakopAutoOk) {
            ImageView checkIv = new ImageView(requireContext());
            LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(lockPx, lockPx);
            checkLp.setMarginEnd(gap1);
            checkIv.setLayoutParams(checkLp);
            checkIv.setImageResource(R.drawable.ic_check_small);
            checkIv.setImageTintList(ColorStateList.valueOf(green));
            checkIv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            checkIv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            nakopScoreWrap.addView(checkIv);
        }

        if (nakopBlockLock) {
            ImageView lockNakop = new ImageView(requireContext());
            lockNakop.setLayoutParams(new LinearLayout.LayoutParams(lockPx, lockPx));
            lockNakop.setImageResource(R.drawable.ic_lock_closed_small);
            lockNakop.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(
                    requireContext(), android.R.color.holo_red_dark)));
            lockNakop.setScaleType(ImageView.ScaleType.FIT_CENTER);
            lockNakop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams lockNakopLp = (LinearLayout.LayoutParams) lockNakop.getLayoutParams();
            lockNakopLp.setMarginEnd(gap1);
            lockNakop.setLayoutParams(lockNakopLp);
            nakopScoreWrap.addView(lockNakop);
        }

        TextView nakopValue = new TextView(requireContext());
        nakopValue.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        nakopValue.setTextAppearance(R.style.TextAppearance_CourseSync_Body);
        nakopValue.setText(nakopValueText(lastCumulativeState, nf));
        nakopScoreWrap.addView(nakopValue);

        FrameLayout nakopTrail = new FrameLayout(requireContext());
        nakopTrail.setLayoutParams(new LinearLayout.LayoutParams(iconPx, iconPx));

        TypedValue trailSelectable = new TypedValue();
        requireContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, trailSelectable, true);

        if (isGroupOwnerForCourseActions) {
            ImageView btnEditNakop = new ImageView(requireContext());
            FrameLayout.LayoutParams pencilLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            btnEditNakop.setLayoutParams(pencilLp);
            btnEditNakop.setPadding(0, 0, 0, 0);
            btnEditNakop.setImageResource(R.drawable.ic_edit);
            btnEditNakop.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pencilTint = MaterialColors.getColor(
                    requireContext(),
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    android.graphics.Color.DKGRAY);
            btnEditNakop.setImageTintList(ColorStateList.valueOf(pencilTint));
            btnEditNakop.setBackgroundResource(trailSelectable.resourceId);
            btnEditNakop.setClickable(true);
            btnEditNakop.setFocusable(true);
            btnEditNakop.setContentDescription(getString(R.string.grading_nakop_edit_content_description));
            btnEditNakop.setOnClickListener(v -> openCumulativeGradeEditorDialog());
            nakopTrail.addView(btnEditNakop);
        } else {
            TextView btnInfoNakop = new TextView(requireContext());
            FrameLayout.LayoutParams infoLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            btnInfoNakop.setLayoutParams(infoLp);
            btnInfoNakop.setGravity(Gravity.CENTER);
            btnInfoNakop.setText("i");
            btnInfoNakop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            btnInfoNakop.setTypeface(Typeface.SERIF, Typeface.ITALIC);
            btnInfoNakop.setBackgroundResource(trailSelectable.resourceId);
            btnInfoNakop.setContentDescription(getString(R.string.grading_nakop_info_content_description));
            btnInfoNakop.setOnClickListener(v -> openCumulativeGradeInfoDialog());
            nakopTrail.addView(btnInfoNakop);
        }

        nakopRow.addView(nakopLeftCol);
        nakopRow.addView(nakopScoreWrap);
        nakopRow.addView(nakopTrail);
        gradingScoresContainer.addView(nakopRow);

        LinearLayout overallRow = new LinearLayout(requireContext());
        overallRow.setOrientation(LinearLayout.HORIZONTAL);
        overallRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams overallRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        overallRowLp.bottomMargin = rowGap;
        overallRow.setLayoutParams(overallRowLp);

        TextView overallLabel = new TextView(requireContext());
        LinearLayout.LayoutParams overallLabelLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        overallLabelLp.setMarginEnd(gap1);
        overallLabel.setLayoutParams(overallLabelLp);
        overallLabel.setTextAppearance(R.style.TextAppearance_CourseSync_Body);
        overallLabel.setText(R.string.grading_overall_average_label);

        LinearLayout overallScoreWrap = new LinearLayout(requireContext());
        overallScoreWrap.setOrientation(LinearLayout.HORIZONTAL);
        overallScoreWrap.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams overallScoreLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        overallScoreLp.setMarginEnd(gap1);
        overallScoreWrap.setLayoutParams(overallScoreLp);

        TextView overallValue = new TextView(requireContext());
        overallValue.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        overallValue.setTextAppearance(R.style.TextAppearance_CourseSync_Body);
        overallValue.setText(formatScoreDisplay(data.averageGrade, nf));
        overallScoreWrap.addView(overallValue);

        View overallTrailSpacer = new View(requireContext());
        overallTrailSpacer.setLayoutParams(new LinearLayout.LayoutParams(iconPx, iconPx));

        overallRow.addView(overallLabel);
        overallRow.addView(overallScoreWrap);
        overallRow.addView(overallTrailSpacer);
        gradingScoresContainer.addView(overallRow);

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

    private void openCumulativeGradeEditorDialog() {
        if (courseId == null || !isGroupOwnerForCourseActions) {
            return;
        }
        final CumulativeGradeState snapshot = lastCumulativeState;
        new Thread(() -> {
            Result<CourseGradingElementListResponse> res =
                    new CourseRepository(App.getDeps().courseApi).getGradingElementOptions(courseId);
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                if (!(res instanceof Result.Success)) {
                    ErrorUi.show(this, R.string.grading_cumulative_load_options_error, ErrorUi.Duration.SHORT);
                    return;
                }
                CourseGradingElementListResponse body =
                        ((Result.Success<CourseGradingElementListResponse>) res).data;
                List<CourseGradingElementOptionItem> options =
                        body != null && body.elements != null ? body.elements : Collections.emptyList();
                showCumulativeGradeDialogUi(options, snapshot, true);
            });
        }).start();
    }

    private void openCumulativeGradeInfoDialog() {
        if (courseId == null || isGroupOwnerForCourseActions) {
            return;
        }
        final CumulativeGradeState snapshot = lastCumulativeState;
        new Thread(() -> {
            Result<CourseGradingElementListResponse> res =
                    new CourseRepository(App.getDeps().courseApi).getGradingElementOptions(courseId);
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                if (!(res instanceof Result.Success)) {
                    ErrorUi.show(this, R.string.grading_cumulative_load_options_error, ErrorUi.Duration.SHORT);
                    return;
                }
                CourseGradingElementListResponse body =
                        ((Result.Success<CourseGradingElementListResponse>) res).data;
                List<CourseGradingElementOptionItem> options =
                        body != null && body.elements != null ? body.elements : Collections.emptyList();
                showCumulativeGradeDialogUi(options, snapshot, false);
            });
        }).start();
    }

    private void showCumulativeGradeDialogUi(
            @NonNull List<CourseGradingElementOptionItem> allOptions,
            @Nullable CumulativeGradeState state,
            boolean editable) {
        View root = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_cumulative_grade, null);
        ChipGroup chipGroup = root.findViewById(R.id.chipGroupCumulativeElements);
        ImageButton btnAdd = root.findViewById(R.id.btnAddCumulativeElement);
        TextInputLayout blockLayout = root.findViewById(R.id.editCumulativeBlockLayout);
        TextInputEditText editBlock = root.findViewById(R.id.editCumulativeBlock);
        TextInputLayout automaticLayout = root.findViewById(R.id.editCumulativeAutomaticLayout);
        TextInputEditText editAutomatic = root.findViewById(R.id.editCumulativeAutomatic);
        TextView blockErrorText = root.findViewById(R.id.textCumulativeBlockError);
        TextView automaticErrorText = root.findViewById(R.id.textCumulativeAutomaticError);
        MaterialButton btnSave = root.findViewById(R.id.btnSaveCumulativeGrade);

        NumberFormat nf = NumberFormat.getNumberInstance(Locale.getDefault());
        List<UUID> initialOrder = buildInitialCumulativeIds(state, allOptions);
        for (UUID id : initialOrder) {
            String label = labelForId(id, allOptions);
            if (label != null) {
                addCumulativeChip(requireContext(), chipGroup, id, label, editable);
            }
        }

        if (state != null && Boolean.TRUE.equals(state.configured) && state.response != null) {
            CourseCumulativeGradeResponse r = state.response;
            double block = r.blockGrade != null ? r.blockGrade : 0.0;
            editBlock.setText(nf.format(block));
            if (r.automatic != null) {
                editAutomatic.setText(nf.format(r.automatic));
            } else {
                editAutomatic.setText("");
            }
        } else {
            editBlock.setText("");
            editAutomatic.setText("");
        }

        cumulativeEditDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.grading_nakop_dialog_title)
                .setView(root)
                .create();

        btnAdd.setVisibility(editable ? View.VISIBLE : View.GONE);
        btnSave.setVisibility(editable ? View.VISIBLE : View.GONE);

        applyCumulativeGradeFieldsEditable(blockLayout, editBlock, automaticLayout, editAutomatic, editable);

        if (editable) {
            TextWatcher clearBlockRange = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    setCumulativeInlineError(blockErrorText, null);
                }

                @Override
                public void afterTextChanged(Editable s) {}
            };
            TextWatcher clearAutoAndCompare = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    setCumulativeInlineError(automaticErrorText, null);
                    setCumulativeInlineError(blockErrorText, null);
                }

                @Override
                public void afterTextChanged(Editable s) {}
            };
            editBlock.addTextChangedListener(clearBlockRange);
            editAutomatic.addTextChangedListener(clearAutoAndCompare);

            btnAdd.setOnClickListener(v -> showCumulativeAddPopup(v, chipGroup, allOptions));
            btnSave.setOnClickListener(v -> {
                setCumulativeInlineError(blockErrorText, null);
                setCumulativeInlineError(automaticErrorText, null);
                List<String> ids = collectCumulativeElementIds(chipGroup);
                if (ids.isEmpty()) {
                    ErrorUi.show(
                            this,
                            cumulativeEditDialog,
                            R.string.grading_cumulative_need_element,
                            ErrorUi.Duration.SHORT);
                    return;
                }
                double blockVal;
                try {
                    blockVal = parseCumulativeBlock(editBlock.getText().toString());
                } catch (IllegalArgumentException ex) {
                    setCumulativeInlineError(
                            blockErrorText, getString(R.string.grading_block_invalid_range));
                    return;
                }
                Double autoVal;
                try {
                    autoVal = parseCumulativeAutomatic(editAutomatic.getText().toString());
                } catch (IllegalArgumentException ex) {
                    setCumulativeInlineError(
                            automaticErrorText, getString(R.string.grading_cumulative_threshold_range));
                    return;
                }
                if (autoVal != null && blockVal > autoVal) {
                    setCumulativeInlineError(
                            blockErrorText, getString(R.string.grading_cumulative_block_gt_auto));
                    return;
                }
                viewModel.saveCumulativeGrade(courseId, ids, blockVal, autoVal);
            });
        }

        cumulativeEditDialog.show();
    }

    private static void setCumulativeInlineError(@Nullable TextView target, @Nullable CharSequence message) {
        if (target == null) {
            return;
        }
        if (message == null || message.length() == 0) {
            target.setText("");
            target.setVisibility(View.GONE);
        } else {
            target.setText(message);
            target.setVisibility(View.VISIBLE);
        }
    }

    private static void applyCumulativeGradeFieldsEditable(
            TextInputLayout blockLayout,
            TextInputEditText block,
            TextInputLayout automaticLayout,
            TextInputEditText automatic,
            boolean editable) {
        blockLayout.setEnabled(editable);
        block.setEnabled(editable);
        block.setFocusable(editable);
        block.setFocusableInTouchMode(editable);
        block.setCursorVisible(editable);
        automaticLayout.setEnabled(editable);
        automatic.setEnabled(editable);
        automatic.setFocusable(editable);
        automatic.setFocusableInTouchMode(editable);
        automatic.setCursorVisible(editable);
        if (!editable) {
            block.setKeyListener(null);
            automatic.setKeyListener(null);
        }
    }

    private static List<UUID> buildInitialCumulativeIds(
            @Nullable CumulativeGradeState state,
            List<CourseGradingElementOptionItem> options) {
        List<UUID> out = new ArrayList<>();
        if (state == null || !Boolean.TRUE.equals(state.configured) || state.response == null) {
            return out;
        }
        List<String> names = state.response.elementNames;
        if (names == null) {
            return out;
        }
        for (String name : names) {
            UUID id = findIdForElementName(name, options);
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    @Nullable
    private static UUID findIdForElementName(String name, List<CourseGradingElementOptionItem> options) {
        if (name == null) {
            return null;
        }
        for (CourseGradingElementOptionItem o : options) {
            if (o != null && name.equals(o.name)) {
                return o.resolveId();
            }
        }
        return null;
    }

    @Nullable
    private static String labelForId(UUID id, List<CourseGradingElementOptionItem> options) {
        for (CourseGradingElementOptionItem o : options) {
            if (o == null) {
                continue;
            }
            UUID oid = o.resolveId();
            if (id.equals(oid)) {
                return o.name != null ? o.name : "";
            }
        }
        return null;
    }

    private void addCumulativeChip(
            android.content.Context context,
            ChipGroup chipGroup,
            UUID elementId,
            String label,
            boolean deletable) {
        Chip chip = new Chip(context, null, com.google.android.material.R.attr.chipStyle);
        chip.setText(label);
        chip.setTag(elementId);
        chip.setCloseIconVisible(deletable);
        chip.setChipIconVisible(false);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setClickable(deletable);
        chip.setFocusable(deletable);
        if (deletable) {
            chip.setOnCloseIconClickListener(v -> chipGroup.removeView(chip));
        }
        chipGroup.addView(chip);
    }

    private void showCumulativeAddPopup(
            View anchor,
            ChipGroup chipGroup,
            List<CourseGradingElementOptionItem> allOptions) {
        Set<UUID> selected = new HashSet<>(collectCumulativeElementUuidSet(chipGroup));
        List<CourseGradingElementOptionItem> addable = new ArrayList<>();
        for (CourseGradingElementOptionItem o : allOptions) {
            if (o == null) {
                continue;
            }
            UUID id = o.resolveId();
            if (id != null && !selected.contains(id)) {
                addable.add(o);
            }
        }
        if (addable.isEmpty()) {
            return;
        }
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        for (int i = 0; i < addable.size(); i++) {
            CourseGradingElementOptionItem o = addable.get(i);
            String title = o.name != null ? o.name : "";
            popup.getMenu().add(Menu.NONE, i, i, title);
        }
        popup.setOnMenuItemClickListener(item -> {
            CourseGradingElementOptionItem o = addable.get(item.getItemId());
            UUID id = o.resolveId();
            if (id != null) {
                String lbl = o.name != null ? o.name : "";
                addCumulativeChip(requireContext(), chipGroup, id, lbl, true);
            }
            return true;
        });
        popup.show();
    }

    private static Set<UUID> collectCumulativeElementUuidSet(ChipGroup chipGroup) {
        Set<UUID> set = new HashSet<>();
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            View ch = chipGroup.getChildAt(i);
            if (ch instanceof Chip) {
                Object tag = ch.getTag();
                if (tag instanceof UUID) {
                    set.add((UUID) tag);
                }
            }
        }
        return set;
    }

    private static List<String> collectCumulativeElementIds(ChipGroup chipGroup) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            View ch = chipGroup.getChildAt(i);
            if (ch instanceof Chip) {
                Object tag = ch.getTag();
                if (tag instanceof UUID) {
                    ids.add(tag.toString());
                }
            }
        }
        return ids;
    }

    private static double parseCumulativeBlock(String raw) {
        try {
            String t = raw != null ? raw.trim().replace(',', '.') : "";
            if (t.isEmpty()) {
                return 0.0;
            }
            double v = Double.parseDouble(t);
            if (v < 0.0 || v > 10.0) {
                throw new IllegalArgumentException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Nullable
    private static Double parseCumulativeAutomatic(String raw) {
        try {
            String t = raw != null ? raw.trim().replace(',', '.') : "";
            if (t.isEmpty()) {
                return null;
            }
            double v = Double.parseDouble(t);
            if (v < 0.0 || v > 10.0) {
                throw new IllegalArgumentException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(e);
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
