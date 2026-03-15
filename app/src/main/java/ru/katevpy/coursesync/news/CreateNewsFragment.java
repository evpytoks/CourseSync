package ru.katevpy.coursesync.news;

import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.util.Result;

public class CreateNewsFragment extends Fragment {

    private TextInputLayout newsNameLayout;
    private TextInputLayout newsDescriptionLayout;
    private CreateNewsViewModel viewModel;

    public CreateNewsFragment() {
        super(R.layout.fragment_create_news);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        newsNameLayout = view.findViewById(R.id.newsNameLayout);
        newsDescriptionLayout = view.findViewById(R.id.newsDescriptionLayout);

        int maxName = getResources().getInteger(R.integer.max_event_name_length);
        int maxDesc = getResources().getInteger(R.integer.max_event_description_length);
        if (newsNameLayout.getEditText() != null) {
            newsNameLayout.getEditText().setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxName)});
        }
        if (newsDescriptionLayout.getEditText() != null) {
            newsDescriptionLayout.getEditText().setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxDesc)});
        }

        viewModel = new ViewModelProvider(this, new CreateNewsViewModelFactory()).get(CreateNewsViewModel.class);

        view.findViewById(R.id.btnCreateNews).setOnClickListener(v -> submit());

        viewModel.getCreateResult().observe(getViewLifecycleOwner(), this::onCreateResult);
    }

    private void submit() {
        String name = newsNameLayout.getEditText() != null ? newsNameLayout.getEditText().getText().toString().trim() : "";
        String description = newsDescriptionLayout.getEditText() != null ? newsDescriptionLayout.getEditText().getText().toString() : "";

        newsNameLayout.setError(null);
        newsDescriptionLayout.setError(null);

        int maxName = getResources().getInteger(R.integer.max_event_name_length);
        int maxDesc = getResources().getInteger(R.integer.max_event_description_length);

        if (name.isEmpty()) {
            newsNameLayout.setError(getString(R.string.enter_news_name));
            return;
        }
        if (name.length() > maxName) {
            newsNameLayout.setError(getString(R.string.news_name_max_length));
            return;
        }
        if (description.length() > maxDesc) {
            newsDescriptionLayout.setError(getString(R.string.news_description_max_length));
            return;
        }

        viewModel.createNews(name, description);
    }

    private void onCreateResult(@Nullable Result<Void> result) {
        if (result == null) return;
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
                Snackbar.make(requireView(), R.string.create_news_server_error, Snackbar.LENGTH_SHORT).show();
                return;
            }
        }
        Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_SHORT).show();
    }
}
