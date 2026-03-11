package ru.katevpy.coursesync.courses;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.SharedGroupViewModel;
import ru.katevpy.coursesync.shared.dto.CourseListItem;
import ru.katevpy.coursesync.shared.util.Result;

public class CoursesFragment extends Fragment {

    private CoursesViewModel viewModel;
    private LinearLayout coursesContainer;
    private TextView coursesNoGroupMessage;
    private View coursesScroll;

    public CoursesFragment() {
        super(R.layout.fragment_courses);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        coursesContainer = view.findViewById(R.id.coursesContainer);
        coursesNoGroupMessage = view.findViewById(R.id.coursesNoGroupMessage);
        coursesScroll = view.findViewById(R.id.coursesScroll);

        viewModel = new ViewModelProvider(
                this,
                new CoursesViewModelFactory(requireContext().getApplicationContext())
        ).get(CoursesViewModel.class);

        SharedGroupViewModel groupVm = new ViewModelProvider(requireActivity()).get(SharedGroupViewModel.class);
        groupVm.getGroupState().observe(getViewLifecycleOwner(), state -> {
            if (state != null && state.hasGroup()) {
                coursesNoGroupMessage.setVisibility(View.GONE);
                coursesScroll.setVisibility(View.VISIBLE);
                viewModel.loadCourses();
            } else {
                coursesNoGroupMessage.setVisibility(View.VISIBLE);
                coursesScroll.setVisibility(View.GONE);
            }
        });

        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);
    }

    private void onLoadResult(@Nullable Result<List<CourseListItem>> result) {
        if (result == null) return;

        if (result instanceof Result.Success) {
            List<CourseListItem> items = ((Result.Success<List<CourseListItem>>) result).data;
            renderCourses(items);
            return;
        }

        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<List<CourseListItem>>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            if (code == 500) {
                Snackbar.make(requireView(), R.string.groups_load_error, Snackbar.LENGTH_LONG).show();
                return;
            }
        }

        Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_LONG).show();
    }

    private void renderCourses(List<CourseListItem> items) {
        coursesContainer.removeAllViews();
        if (items == null) return;
        int marginBottom = (int) (12 * getResources().getDisplayMetrics().density);
        for (CourseListItem item : items) {
            MaterialButton btn = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(item.name != null ? item.name : "");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = marginBottom;
            btn.setLayoutParams(lp);
            coursesContainer.addView(btn);
        }
    }
}
