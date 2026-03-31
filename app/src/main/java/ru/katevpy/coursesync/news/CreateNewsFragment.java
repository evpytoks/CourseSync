package ru.katevpy.coursesync.news;

import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.GroupState;
import ru.katevpy.coursesync.shared.SharedGroupViewModel;
import ru.katevpy.coursesync.shared.dto.OwnerGroupListItem;
import ru.katevpy.coursesync.shared.dto.OwnerGroupListResponse;
import ru.katevpy.coursesync.shared.util.Result;
import ru.katevpy.coursesync.ui.ErrorUi;

public class CreateNewsFragment extends Fragment {

    private TextView newsOwnerEmpty;
    private Spinner newsGroupSpinner;
    private TextInputLayout newsDescriptionLayout;
    private View btnCreateNews;
    private CreateNewsViewModel viewModel;
    private List<OwnerGroupListItem> ownerItems = new ArrayList<>();

    public CreateNewsFragment() {
        super(R.layout.fragment_create_news);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        newsOwnerEmpty = view.findViewById(R.id.newsOwnerEmpty);
        newsGroupSpinner = view.findViewById(R.id.newsGroupSpinner);
        newsDescriptionLayout = view.findViewById(R.id.newsDescriptionLayout);
        btnCreateNews = view.findViewById(R.id.btnCreateNews);

        int maxDesc = getResources().getInteger(R.integer.max_news_description_length);
        if (newsDescriptionLayout.getEditText() != null) {
            newsDescriptionLayout.getEditText().setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxDesc)});
        }

        viewModel = new ViewModelProvider(this, new CreateNewsViewModelFactory()).get(CreateNewsViewModel.class);

        btnCreateNews.setOnClickListener(v -> submit());
        viewModel.getCreateResult().observe(getViewLifecycleOwner(), this::onCreateResult);
        viewModel.getOwnerGroupsLoad().observe(getViewLifecycleOwner(), this::onOwnerGroupsLoaded);

        viewModel.loadOwnerGroups();
    }

    private void onOwnerGroupsLoaded(@Nullable Result<OwnerGroupListResponse> result) {
        if (result == null) {
            return;
        }
        if (result instanceof Result.Success) {
            OwnerGroupListResponse data = ((Result.Success<OwnerGroupListResponse>) result).data;
            List<OwnerGroupListItem> items = data != null && data.groups != null ? data.groups : new ArrayList<>();
            ownerItems = new ArrayList<>(items);
            if (ownerItems.isEmpty()) {
                newsOwnerEmpty.setVisibility(View.VISIBLE);
                newsGroupSpinner.setEnabled(false);
                btnCreateNews.setEnabled(false);
                return;
            }
            newsOwnerEmpty.setVisibility(View.GONE);
            newsGroupSpinner.setEnabled(true);
            btnCreateNews.setEnabled(true);
            List<String> labels = new ArrayList<>();
            for (OwnerGroupListItem it : ownerItems) {
                labels.add(it.name != null ? it.name.trim() : "");
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    requireContext(), android.R.layout.simple_spinner_item, labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            newsGroupSpinner.setAdapter(adapter);
            int sel = 0;
            SharedGroupViewModel gvm = new ViewModelProvider(requireActivity()).get(SharedGroupViewModel.class);
            GroupState gs = gvm.getGroupState().getValue();
            String preferredId = gs != null ? gs.groupId : null;
            if (preferredId != null) {
                for (int i = 0; i < ownerItems.size(); i++) {
                    String id = ownerItems.get(i).id;
                    if (id != null && preferredId.equalsIgnoreCase(id.trim())) {
                        sel = i;
                        break;
                    }
                }
            }
            newsGroupSpinner.setSelection(sel);
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<OwnerGroupListResponse>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
        }
        if (result instanceof Result.NetworkError) {
            newsOwnerEmpty.setText(R.string.network_error);
        } else {
            newsOwnerEmpty.setText(R.string.news_owner_groups_load_error);
        }
        newsOwnerEmpty.setVisibility(View.VISIBLE);
        newsGroupSpinner.setEnabled(false);
        btnCreateNews.setEnabled(false);
    }

    private void submit() {
        newsDescriptionLayout.setError(null);
        if (ownerItems.isEmpty()) {
            return;
        }
        int pos = newsGroupSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= ownerItems.size()) {
            ErrorUi.show(this, R.string.news_select_group_error, ErrorUi.Duration.SHORT);
            return;
        }
        UUID groupId;
        try {
            groupId = UUID.fromString(ownerItems.get(pos).id.trim());
        } catch (Exception e) {
            ErrorUi.show(this, R.string.news_select_group_error, ErrorUi.Duration.SHORT);
            return;
        }
        String description = newsDescriptionLayout.getEditText() != null
                ? newsDescriptionLayout.getEditText().getText().toString().trim() : "";
        int maxDesc = getResources().getInteger(R.integer.max_news_description_length);
        if (description.isEmpty()) {
            newsDescriptionLayout.setError(getString(R.string.enter_news_text));
            return;
        }
        if (description.length() > maxDesc) {
            newsDescriptionLayout.setError(getString(R.string.news_description_max_length));
            return;
        }
        viewModel.createNews(groupId, description);
    }

    private void onCreateResult(@Nullable Result<Void> result) {
        if (result == null) {
            return;
        }
        if (result instanceof Result.Success) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<Void>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.create_news_server_error, ErrorUi.Duration.SHORT);
                return;
            }
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
    }
}
