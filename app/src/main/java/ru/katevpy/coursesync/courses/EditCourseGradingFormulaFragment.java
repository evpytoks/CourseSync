package ru.katevpy.coursesync.courses;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CourseGradingElementItem;
import ru.katevpy.coursesync.shared.dto.CourseGradingTextResponse;
import ru.katevpy.coursesync.shared.util.Result;

public class EditCourseGradingFormulaFragment extends Fragment {

    private TextInputLayout descriptionLayout;
    private LinearLayout gradingElementsRows;
    private EditCourseGradingFormulaViewModel viewModel;
    private UUID courseId;

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

        view.findViewById(R.id.btnAddControlElement).setOnClickListener(v -> { });
        view.findViewById(R.id.btnSaveGradingFormula).setOnClickListener(v -> { });

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
                Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }
            if (code == 500) {
                Snackbar.make(requireView(), R.string.grading_formula_load_error, Snackbar.LENGTH_SHORT).show();
                return;
            }
        }
        if (result instanceof Result.NetworkError) {
            Snackbar.make(requireView(), R.string.network_error, Snackbar.LENGTH_SHORT).show();
            return;
        }
        Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_SHORT).show();
    }

    private void onGradingElementsResult(@Nullable Result<List<CourseGradingElementItem>> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
            List<CourseGradingElementItem> items = ((Result.Success<List<CourseGradingElementItem>>) result).data;
            renderGradingElements(items);
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<List<CourseGradingElementItem>>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            if (code == 403 || code == 404 || code == 400) {
                Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }
            if (code == 500) {
                Snackbar.make(requireView(), R.string.grading_elements_load_error, Snackbar.LENGTH_SHORT).show();
                return;
            }
        }
        if (result instanceof Result.NetworkError) {
            Snackbar.make(requireView(), R.string.network_error, Snackbar.LENGTH_SHORT).show();
            return;
        }
        Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_SHORT).show();
    }

    private void renderGradingElements(@Nullable List<CourseGradingElementItem> items) {
        if (gradingElementsRows == null) {
            return;
        }
        gradingElementsRows.removeAllViews();
        if (items == null || items.isEmpty()) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.getDefault());
        for (CourseGradingElementItem item : items) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = (int) (4 * density);
            row.setLayoutParams(rowLp);

            TextView nameCol = new TextView(requireContext());
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nameLp.setMarginEnd((int) (8 * density));
            nameCol.setLayoutParams(nameLp);
            nameCol.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            nameCol.setText(item.name != null ? item.name : "");

            TextView coefCol = new TextView(requireContext());
            coefCol.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            coefCol.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            coefCol.setText(formatCoefficient(item.coefficient, nf));

            row.addView(nameCol);
            row.addView(coefCol);
            gradingElementsRows.addView(row);
        }
    }

    private static String formatCoefficient(@Nullable Double c, NumberFormat nf) {
        if (c == null) {
            return "—";
        }
        return nf.format(c);
    }
}
