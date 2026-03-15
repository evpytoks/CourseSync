package ru.katevpy.coursesync.news;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.dto.NewsDetailsResponse;
import ru.katevpy.coursesync.shared.util.Result;

public class NewsDetailFragment extends Fragment {

    private TextView newsDetailName;
    private TextView newsDetailTime;
    private TextView newsDetailDescription;
    private UUID newsId;

    public NewsDetailFragment() {
        super(R.layout.fragment_news_detail);
    }

    @Override
    public void onViewCreated(@NonNull android.view.View view, @Nullable android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        newsDetailName = view.findViewById(R.id.newsDetailName);
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
            newsDetailName.setText(data.name != null ? data.name : "");
            newsDetailTime.setText(formatTime(data.createdAt));
            newsDetailDescription.setText(data.description != null ? data.description : "");
            newsDetailDescription.setVisibility(
                    data.description != null && !data.description.isEmpty() ? View.VISIBLE : View.GONE);
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
                Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).navigateUp();
                return;
            }
            if (code == 500) {
                Snackbar.make(requireView(), R.string.news_load_error, Snackbar.LENGTH_SHORT).show();
                return;
            }
        }
        Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_SHORT).show();
    }

    private static final DateTimeFormatter OUTPUT_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", new Locale("ru"));

    private static String formatTime(String isoStr) {
        if (isoStr == null || isoStr.isEmpty()) return "";
        String s = isoStr.trim();
        try {
            if (s.length() >= 19) {
                LocalDateTime ldt = LocalDateTime.parse(s.substring(0, 19), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return ldt.format(OUTPUT_FORMAT);
            }
            if (s.length() >= 16 && s.charAt(10) == 'T') {
                LocalDateTime ldt = LocalDateTime.parse(s.substring(0, 16), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
                return ldt.format(OUTPUT_FORMAT);
            }
            Instant instant = Instant.parse(s);
            return OUTPUT_FORMAT.withZone(ZoneId.systemDefault()).format(instant);
        } catch (DateTimeParseException ignored) {
        }
        return "";
    }
}
