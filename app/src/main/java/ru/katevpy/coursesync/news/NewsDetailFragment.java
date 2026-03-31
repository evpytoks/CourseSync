package ru.katevpy.coursesync.news;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import ru.katevpy.coursesync.ui.ErrorUi;

import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.NewsDetailsResponse;
import ru.katevpy.coursesync.shared.util.Result;

public class NewsDetailFragment extends Fragment {

    private TextView newsDetailTime;
    private TextView newsDetailDescription;
    private UUID newsId;

    public NewsDetailFragment() {
        super(R.layout.fragment_news_detail);
    }

    @Override
    public void onViewCreated(@NonNull android.view.View view, @Nullable android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        newsDetailTime = view.findViewById(R.id.newsDetailTime);
        newsDetailDescription = view.findViewById(R.id.newsDetailDescription);

        if (getArguments() == null) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        String idStr = getArguments().getString("newsId");
        if (idStr == null || idStr.isEmpty()) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }
        try {
            newsId = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        NewsDetailViewModel viewModel = new ViewModelProvider(this, new NewsDetailViewModelFactory()).get(NewsDetailViewModel.class);
        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (newsId != null) {
            NewsDetailViewModel vm = new ViewModelProvider(this, new NewsDetailViewModelFactory()).get(NewsDetailViewModel.class);
            vm.loadNews(newsId);
        }
    }

    private void onLoadResult(@Nullable Result<NewsDetailsResponse> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
            NewsDetailsResponse data = ((Result.Success<NewsDetailsResponse>) result).data;
            String timeStr = NewsDateTime.format(data.time);
            if (timeStr == null || timeStr.isEmpty()) {
                newsDetailTime.setVisibility(View.GONE);
            } else {
                newsDetailTime.setVisibility(View.VISIBLE);
                newsDetailTime.setText(timeStr);
            }
            String body = data.text != null ? data.text : "";
            newsDetailDescription.setText(body);
            newsDetailDescription.setVisibility(!body.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<NewsDetailsResponse>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            if (code == 404 || code == 403) {
                ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }
            if (code == 500) {
                ErrorUi.show(this, R.string.news_load_error, ErrorUi.Duration.SHORT);
                return;
            }
        }
        ErrorUi.show(this, R.string.internal_error, ErrorUi.Duration.SHORT);
    }
}
