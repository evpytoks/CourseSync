package ru.katevpy.coursesync.courses;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.SharedGroupViewModel;
import ru.katevpy.coursesync.shared.dto.CourseListItem;
import ru.katevpy.coursesync.shared.util.Result;
import ru.katevpy.coursesync.ui.ErrorUi;
import ru.katevpy.coursesync.ui.ListSpacingDecoration;

public class CoursesFragment extends Fragment {

    private CoursesViewModel viewModel;
    private RecyclerView coursesRecycler;
    private TextView coursesNoGroupMessage;
    private View errorBanner;
    private CourseListAdapter listAdapter;

    public CoursesFragment() {
        super(R.layout.fragment_courses);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        coursesRecycler = view.findViewById(R.id.coursesRecycler);
        coursesNoGroupMessage = view.findViewById(R.id.coursesNoGroupMessage);
        errorBanner = view.findViewById(R.id.errorBanner);

        coursesRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        int spacing = getResources().getDimensionPixelSize(R.dimen.grid_1);
        coursesRecycler.addItemDecoration(new ListSpacingDecoration(spacing));

        listAdapter = new CourseListAdapter(courseId -> {
            Bundle args = new Bundle();
            args.putString("courseId", courseId.toString());
            NavHostFragment.findNavController(CoursesFragment.this)
                    .navigate(R.id.action_coursesFragment_to_courseDetailFragment, args);
        });
        coursesRecycler.setAdapter(listAdapter);

        viewModel = new ViewModelProvider(
                this,
                new CoursesViewModelFactory(requireContext().getApplicationContext())
        ).get(CoursesViewModel.class);

        SharedGroupViewModel groupVm = new ViewModelProvider(requireActivity()).get(SharedGroupViewModel.class);
        groupVm.getGroupState().observe(getViewLifecycleOwner(), state -> {
            if (state != null && state.hasGroup()) {
                coursesNoGroupMessage.setVisibility(View.GONE);
                coursesRecycler.setVisibility(View.VISIBLE);
                viewModel.loadCourses();
            } else {
                ErrorUi.hideErrorBanner(errorBanner);
                listAdapter.submitList(null);
                coursesNoGroupMessage.setVisibility(View.VISIBLE);
                coursesRecycler.setVisibility(View.GONE);
            }
        });

        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);
    }

    private void onLoadResult(@Nullable Result<List<CourseListItem>> result) {
        if (result == null) return;

        if (result instanceof Result.Success) {
            ErrorUi.hideErrorBanner(errorBanner);
            List<CourseListItem> items = ((Result.Success<List<CourseListItem>>) result).data;
            listAdapter.submitList(items);
            return;
        }

        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<List<CourseListItem>>) result).httpCode;
            if (code == 401) {
                ErrorUi.hideErrorBanner(errorBanner);
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            if (code == 500) {
                listAdapter.submitList(null);
                ErrorUi.showErrorBanner(errorBanner, R.string.courses_list_load_error, () -> viewModel.loadCourses());
                return;
            }
        }

        listAdapter.submitList(null);
        ErrorUi.showErrorBanner(errorBanner, R.string.internal_error, () -> viewModel.loadCourses());
    }
}
