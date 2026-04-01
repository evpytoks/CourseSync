package ru.katevpy.coursesync.courses;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import ru.katevpy.coursesync.ui.ErrorUi;

import java.util.List;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.CourseDetailsResponse;
import ru.katevpy.coursesync.shared.dto.CourseUsefulLinkItem;
import ru.katevpy.coursesync.shared.util.Result;

public class CourseDetailFragment extends Fragment {

    private TextView courseDetailName;
    private TextView courseDetailGeneral;
    private LinearLayout courseDetailLinksList;
    private View courseDetailLinksCard;
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
        courseDetailLinksList = view.findViewById(R.id.courseDetailLinksList);
        courseDetailLinksCard = view.findViewById(R.id.courseDetailLinksCard);

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
        view.findViewById(R.id.courseGradingFormula).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("courseId", courseIdStr);
            NavHostFragment.findNavController(CourseDetailFragment.this)
                    .navigate(R.id.action_courseDetailFragment_to_courseGradingFormulaFragment, args);
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
            bindUsefulLinks(data.usefulLinks);
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
                ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }
            if (code == 400) {
                ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.course_load_error, ErrorUi.Duration.SHORT);
                return;
            }
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
    }

    private void bindUsefulLinks(@Nullable List<CourseUsefulLinkItem> links) {
        courseDetailLinksList.removeAllViews();
        if (links == null || links.isEmpty()) {
            courseDetailLinksCard.setVisibility(View.GONE);
            return;
        }
        courseDetailLinksCard.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (CourseUsefulLinkItem item : links) {
            if (item == null) {
                continue;
            }
            String url = item.url != null ? item.url.trim() : "";
            if (url.isEmpty()) {
                continue;
            }
            View row = inflater.inflate(R.layout.item_course_useful_link, courseDetailLinksList, false);
            TextView titleView = row.findViewById(R.id.courseUsefulLinkTitle);
            String title = item.title != null ? item.title.trim() : "";
            if (title.isEmpty()) {
                title = getString(R.string.link_default_title);
            }
            titleView.setText(title);
            row.setContentDescription(getString(R.string.course_useful_link_row_a11y, title));
            row.setOnClickListener(v -> openExternalUrl(url));
            courseDetailLinksList.addView(row);
        }
        if (courseDetailLinksList.getChildCount() == 0) {
            courseDetailLinksCard.setVisibility(View.GONE);
        }
    }

    private void openExternalUrl(@NonNull String raw) {
        String u = raw.trim();
        if (u.isEmpty()) {
            return;
        }
        if (!u.matches("(?i)https?://.*")) {
            u = "https://" + u;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
        } catch (Exception e) {
            ErrorUi.show(this, R.string.link_open_error, ErrorUi.Duration.SHORT);
        }
    }
}
