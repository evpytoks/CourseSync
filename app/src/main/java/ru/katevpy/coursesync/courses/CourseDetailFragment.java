package ru.katevpy.coursesync.courses;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;

import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CourseDetailsResponse;
import ru.katevpy.coursesync.shared.util.Result;

public class CourseDetailFragment extends Fragment {

    private TextView courseDetailName;
    private TextView courseDetailGeneral;
    private TextView courseDetailUsefulLinksLabel;
    private TextView courseDetailUsefulLinks;
    private UUID courseUuid;
    private String courseIdStr;

    public CourseDetailFragment() {
        super(R.layout.fragment_course_detail);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        courseDetailName = view.findViewById(R.id.courseDetailName);
        courseDetailGeneral = view.findViewById(R.id.courseDetailGeneral);
        courseDetailUsefulLinksLabel = view.findViewById(R.id.courseDetailUsefulLinksLabel);
        courseDetailUsefulLinks = view.findViewById(R.id.courseDetailUsefulLinks);

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
            courseUuid = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        courseIdStr = idStr;

        view.findViewById(R.id.courseMaterialsShared).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("courseId", courseIdStr);
            NavHostFragment.findNavController(CourseDetailFragment.this)
                    .navigate(R.id.action_courseDetailFragment_to_courseSharedMaterialsFragment, args);
        });
        view.findViewById(R.id.courseMaterialsPersonal).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("courseId", courseIdStr);
            NavHostFragment.findNavController(CourseDetailFragment.this)
                    .navigate(R.id.action_courseDetailFragment_to_coursePersonalMaterialsFragment, args);
        });

        CourseDetailViewModel viewModel = new ViewModelProvider(this, new CourseDetailViewModelFactory())
                .get(CourseDetailViewModel.class);
        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (courseUuid != null) {
            CourseDetailViewModel vm = new ViewModelProvider(this, new CourseDetailViewModelFactory())
                    .get(CourseDetailViewModel.class);
            vm.loadCourse(courseUuid);
        }
    }

    private void onLoadResult(@Nullable Result<CourseDetailsResponse> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
            CourseDetailsResponse data = ((Result.Success<CourseDetailsResponse>) result).data;
            if (data == null) return;
            courseDetailName.setText(data.name != null ? data.name : "");
            courseDetailGeneral.setText(data.generalInfo != null ? data.generalInfo : "");
            String links = data.usefulLinks != null ? data.usefulLinks : "";
            courseDetailUsefulLinks.setText(links);
            boolean showLinks = !links.isEmpty();
            courseDetailUsefulLinksLabel.setVisibility(showLinks ? View.VISIBLE : View.GONE);
            courseDetailUsefulLinks.setVisibility(showLinks ? View.VISIBLE : View.GONE);
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<CourseDetailsResponse>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            if (code == 403 || code == 404) {
                Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }
            if (code == 400) {
                Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }
            if (code == 500) {
                Snackbar.make(requireView(), R.string.course_load_error, Snackbar.LENGTH_SHORT).show();
                return;
            }
        }
        Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_SHORT).show();
    }
}
