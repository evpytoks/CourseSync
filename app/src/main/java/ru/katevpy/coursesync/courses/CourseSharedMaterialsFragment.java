package ru.katevpy.coursesync.courses;

import android.content.ActivityNotFoundException;
import android.content.Intent;
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
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ru.katevpy.coursesync.ui.ErrorUi;

import java.io.File;
import java.util.List;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.ApiError;
import ru.katevpy.coursesync.shared.dto.CourseMaterialListItem;
import ru.katevpy.coursesync.shared.util.Result;

public class CourseSharedMaterialsFragment extends Fragment {

    private static final String FILE_PROVIDER_AUTHORITY = "ru.katevpy.coursesync.fileprovider";

    private final ActivityResultLauncher<String> pickPdfLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPdfPicked);

    private LinearLayout materialsList;
    private TextView emptyView;
    private MaterialButton btnAddDocument;
    private CourseSharedMaterialsViewModel viewModel;
    private UUID courseId;
    private boolean isOwner;

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
        viewModel.getDownloadForViewResult().observe(getViewLifecycleOwner(), this::onDownloadForViewResult);
        viewModel.getDeleteResult().observe(getViewLifecycleOwner(), this::onDeleteResult);
        viewModel.getOwnerState().observe(getViewLifecycleOwner(), owner -> {
            isOwner = owner != null && owner;
            if (btnAddDocument != null) {
                btnAddDocument.setVisibility(Boolean.TRUE.equals(owner) ? View.VISIBLE : View.GONE);
            }
            viewModel.loadGeneralMaterials(courseId);
        });
        viewModel.getUploadInProgress().observe(getViewLifecycleOwner(), busy -> {
            if (btnAddDocument != null) {
                btnAddDocument.setEnabled(busy == null || !busy);
            }
        });
        viewModel.getPdfOpenInProgress().observe(getViewLifecycleOwner(), busy ->
                materialsList.setAlpha(busy != null && busy ? 0.6f : 1f));

        btnAddDocument.setOnClickListener(v -> pickPdfLauncher.launch("application/pdf"));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (courseId != null && viewModel != null) {
            viewModel.loadOwnerStateFromCurrentGroup();
            viewModel.loadGeneralMaterials(courseId);
        }
    }

    private void onPdfPicked(@Nullable Uri uri) {
        if (!isAdded() || uri == null || courseId == null || viewModel == null || !isOwner) {
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
                ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.shared_materials_load_error, ErrorUi.Duration.SHORT);
                return;
            }
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
    }

    private void onDownloadForViewResult(@Nullable Result<File> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
            File file = ((Result.Success<File>) result).data;
            if (file == null || !file.exists()) {
                ErrorUi.show(this, R.string.material_pdf_load_error, ErrorUi.Duration.LONG);
            } else {
                Uri uri = FileProvider.getUriForFile(requireContext(), FILE_PROVIDER_AUTHORITY, file);
                Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                viewIntent.setDataAndType(uri, "application/pdf");
                viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(Intent.createChooser(viewIntent, null));
                } catch (ActivityNotFoundException e) {
                    ErrorUi.show(this, R.string.material_pdf_open_error, ErrorUi.Duration.LONG);
                }
            }
        } else if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<File>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
            } else if (code == 403 || code == 404) {
                ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.LONG);
            } else if (code == 500) {
                ErrorUi.show(this, R.string.material_pdf_load_error, ErrorUi.Duration.LONG);
            } else {
                ErrorUi.show(this, R.string.material_pdf_load_error, ErrorUi.Duration.LONG);
            }
        } else if (result instanceof Result.NetworkError) {
            ErrorUi.show(this, R.string.network_error, ErrorUi.Duration.LONG);
        } else {
            ErrorUi.show(this, R.string.material_pdf_load_error, ErrorUi.Duration.LONG);
        }
        viewModel.clearDownloadForViewResult();
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
                ErrorUi.show(this, R.string.material_upload_not_pdf, ErrorUi.Duration.LONG);
                return;
            }
            if ("file_too_large".equals(code)) {
                ErrorUi.show(this, R.string.material_upload_too_large, ErrorUi.Duration.LONG);
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
                ErrorUi.show(this, R.string.material_upload_forbidden, ErrorUi.Duration.LONG);
                return;
            }
            if (he.httpCode == 400) {
                if (materialUploadErrorFromApi(he.error)) {
                    return;
                }
            }
            if (he.httpCode == 500) {
                ErrorUi.show(this, R.string.material_upload_server_error, ErrorUi.Duration.LONG);
                return;
            }
        }
        if (result instanceof Result.NetworkError) {
            ErrorUi.show(this, R.string.network_error, ErrorUi.Duration.LONG);
            return;
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.LONG);
    }

    private void onDeleteResult(@Nullable Result<Void> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
            if (courseId != null) {
                viewModel.loadGeneralMaterials(courseId);
            }
            return;
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
            if (he.httpCode == 500) {
                ErrorUi.show(this, R.string.material_delete_server_error, ErrorUi.Duration.LONG);
                return;
            }
        }
        if (result instanceof Result.NetworkError) {
            ErrorUi.show(this, R.string.network_error, ErrorUi.Duration.LONG);
            return;
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.LONG);
    }

    private void askDeleteMaterial(@NonNull CourseMaterialListItem item) {
        if (courseId == null || item.id == null) return;
        String name = item.name != null && !item.name.trim().isEmpty() ? item.name.trim() : "документ";
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.delete_material_confirm, name))
                .setPositiveButton(R.string.event_yes, (d, which) ->
                        viewModel.deleteGeneralMaterial(courseId, item.id))
                .setNegativeButton(R.string.event_no, null)
                .show();
    }

    private boolean materialUploadErrorFromApi(@Nullable ApiError error) {
        if (error == null || error.code == null) {
            return false;
        }
        switch (error.code) {
            case "not_pdf":
                ErrorUi.show(this, R.string.material_upload_not_pdf, ErrorUi.Duration.LONG);
                return true;
            case "file_too_large":
                ErrorUi.show(this, R.string.material_upload_too_large, ErrorUi.Duration.LONG);
                return true;
            case "file_required":
                ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.LONG);
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
        android.content.res.Resources res = getResources();
        int marginBottom = res.getDimensionPixelSize(R.dimen.grid_2);
        int cardPadding = res.getDimensionPixelSize(R.dimen.card_content_padding);
        float cardRadius = res.getDimension(R.dimen.card_corner_radius);
        float cardElev = res.getDimension(R.dimen.card_elevation_default);
        for (CourseMaterialListItem item : items) {
            MaterialCardView card = new MaterialCardView(requireContext());
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.bottomMargin = marginBottom;
            card.setLayoutParams(cardLp);
            card.setRadius(cardRadius);
            card.setCardElevation(cardElev);
            card.setUseCompatPadding(true);
            card.setClickable(true);
            card.setFocusable(true);
            UUID materialId = item.id;
            if (materialId != null && courseId != null) {
                card.setOnClickListener(v ->
                        viewModel.downloadGeneralPdfForView(courseId, materialId));
            }

            LinearLayout inner = new LinearLayout(requireContext());
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            inner.setPadding(cardPadding, cardPadding, cardPadding, cardPadding);

            TextView title = new TextView(requireContext());
            title.setTextAppearance(R.style.TextAppearance_CourseSync_BodyEmphasized);
            title.setText(item.name != null ? item.name : "");

            TextView meta = new TextView(requireContext());
            meta.setTextAppearance(R.style.TextAppearance_CourseSync_Caption);
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

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(inner);

            if (isOwner) {
                MaterialButton deleteBtn = new MaterialButton(
                        requireContext(),
                        null,
                        com.google.android.material.R.attr.materialIconButtonStyle
                );
                deleteBtn.setIconResource(android.R.drawable.ic_menu_close_clear_cancel);
                deleteBtn.setInsetTop(0);
                deleteBtn.setInsetBottom(0);
                deleteBtn.setOnClickListener(v -> askDeleteMaterial(item));
                row.addView(deleteBtn);
            }

            card.addView(row);
            materialsList.addView(card);
        }
    }
}
