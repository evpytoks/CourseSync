package ru.katevpy.coursesync.courses;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.ApiError;
import ru.katevpy.coursesync.shared.dto.CourseMaterialListItem;
import ru.katevpy.coursesync.shared.util.Result;

public class CourseSharedMaterialsFragment extends Fragment {

    private final ActivityResultLauncher<String> pickPdfLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPdfPicked);

    private LinearLayout materialsList;
    private TextView emptyView;
    private MaterialButton btnAddDocument;
    private CourseSharedMaterialsViewModel viewModel;
    private UUID courseId;

    public CourseSharedMaterialsFragment() {
        super(R.layout.fragment_course_shared_materials);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        materialsList = view.findViewById(R.id.sharedMaterialsList);
        emptyView = view.findViewById(R.id.sharedMaterialsEmpty);
        btnAddDocument = view.findViewById(R.id.btnAddDocument);

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

        viewModel = new ViewModelProvider(
                this,
                new CourseSharedMaterialsViewModelFactory(requireActivity().getApplication())
        ).get(CourseSharedMaterialsViewModel.class);

        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);
        viewModel.getUploadResult().observe(getViewLifecycleOwner(), this::onUploadResult);
        viewModel.getUploadInProgress().observe(getViewLifecycleOwner(), busy -> {
            if (btnAddDocument != null) {
                btnAddDocument.setEnabled(busy == null || !busy);
            }
        });

        btnAddDocument.setOnClickListener(v -> pickPdfLauncher.launch("application/pdf"));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (courseId != null && viewModel != null) {
            viewModel.loadGeneralMaterials(courseId);
        }
    }

    private void onPdfPicked(@Nullable Uri uri) {
        if (!isAdded() || uri == null || courseId == null || viewModel == null) {
            return;
        }
        viewModel.uploadGeneralMaterial(courseId, uri);
    }

    private void onLoadResult(@Nullable Result<List<CourseMaterialListItem>> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
            List<CourseMaterialListItem> items = ((Result.Success<List<CourseMaterialListItem>>) result).data;
            renderList(items);
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<List<CourseMaterialListItem>>) result).httpCode;
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
                Snackbar.make(requireView(), R.string.shared_materials_load_error, Snackbar.LENGTH_SHORT).show();
                return;
            }
        }
        Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_SHORT).show();
    }

    private void onUploadResult(@Nullable Result<Void> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
            if (courseId != null) {
                viewModel.loadGeneralMaterials(courseId);
            }
            return;
        }
        if (result instanceof Result.LogicalError) {
            String code = ((Result.LogicalError<Void>) result).message;
            if ("not_pdf".equals(code)) {
                Snackbar.make(requireView(), R.string.material_upload_not_pdf, Snackbar.LENGTH_LONG).show();
                return;
            }
            if ("file_too_large".equals(code)) {
                Snackbar.make(requireView(), R.string.material_upload_too_large, Snackbar.LENGTH_LONG).show();
                return;
            }
        }
        if (result instanceof Result.HttpError) {
            Result.HttpError<Void> he = (Result.HttpError<Void>) result;
            if (he.httpCode == 401) {
                App.getDeps().tokenStorage.clear();
                NavOptions opts = new NavOptions.Builder()
                        .setPopUpTo(R.id.groupsFragment, true)
                        .build();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment, null, opts);
                return;
            }
            if (he.httpCode == 403) {
                Snackbar.make(requireView(), R.string.material_upload_forbidden, Snackbar.LENGTH_LONG).show();
                return;
            }
            if (he.httpCode == 400) {
                if (materialUploadErrorFromApi(he.error)) {
                    return;
                }
            }
            if (he.httpCode == 500) {
                Snackbar.make(requireView(), R.string.material_upload_server_error, Snackbar.LENGTH_LONG).show();
                return;
            }
        }
        if (result instanceof Result.NetworkError) {
            Snackbar.make(requireView(), R.string.network_error, Snackbar.LENGTH_LONG).show();
            return;
        }
        Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_LONG).show();
    }

    private boolean materialUploadErrorFromApi(@Nullable ApiError error) {
        if (error == null || error.code == null) {
            return false;
        }
        switch (error.code) {
            case "not_pdf":
                Snackbar.make(requireView(), R.string.material_upload_not_pdf, Snackbar.LENGTH_LONG).show();
                return true;
            case "file_too_large":
                Snackbar.make(requireView(), R.string.material_upload_too_large, Snackbar.LENGTH_LONG).show();
                return true;
            case "file_required":
                Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_LONG).show();
                return true;
            default:
                return false;
        }
    }

    private void renderList(@Nullable List<CourseMaterialListItem> items) {
        materialsList.removeAllViews();
        if (items == null || items.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        emptyView.setVisibility(View.GONE);
        float density = getResources().getDisplayMetrics().density;
        int marginBottom = (int) (12 * density);
        int cardPadding = (int) (16 * density);
        for (CourseMaterialListItem item : items) {
            MaterialCardView card = new MaterialCardView(requireContext());
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.bottomMargin = marginBottom;
            card.setLayoutParams(cardLp);
            card.setCardElevation(1f * density);
            card.setUseCompatPadding(true);

            LinearLayout inner = new LinearLayout(requireContext());
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setPadding(cardPadding, cardPadding, cardPadding, cardPadding);

            TextView title = new TextView(requireContext());
            title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
            title.setText(item.name != null ? item.name : "");

            TextView meta = new TextView(requireContext());
            meta.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            String author = item.authorEmail != null ? item.authorEmail : "";
            String at = item.createdAt != null ? item.createdAt : "";
            String metaLine = author;
            if (!at.isEmpty()) {
                metaLine = metaLine.isEmpty() ? at : author + "\n" + at;
            }
            meta.setText(metaLine);

            inner.addView(title);
            if (!metaLine.isEmpty()) {
                inner.addView(meta);
            }
            card.addView(inner);
            materialsList.addView(card);
        }
    }
}
