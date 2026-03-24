package ru.katevpy.coursesync.courses;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import ru.katevpy.coursesync.R;

public class CourseDetailFragment extends Fragment {

    public CourseDetailFragment() {
        super(R.layout.fragment_course_detail);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String courseId = null;
        if (getArguments() != null) {
            courseId = getArguments().getString("courseId");
        }
        if (courseId == null || courseId.isEmpty()) {
            return;
        }
        String finalCourseId = courseId;
        view.findViewById(R.id.courseMaterialsShared).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("courseId", finalCourseId);
            NavHostFragment.findNavController(CourseDetailFragment.this)
                    .navigate(R.id.action_courseDetailFragment_to_courseSharedMaterialsFragment, args);
        });
        view.findViewById(R.id.courseMaterialsPersonal).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("courseId", finalCourseId);
            NavHostFragment.findNavController(CourseDetailFragment.this)
                    .navigate(R.id.action_courseDetailFragment_to_coursePersonalMaterialsFragment, args);
        });
    }
}
