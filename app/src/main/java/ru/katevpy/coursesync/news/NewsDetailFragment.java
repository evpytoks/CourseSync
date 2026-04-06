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

    private TextView newsDetailDescription;
    private UUID newsId;

    public NewsDetailFragment() {
        super(R.layout.fragment_news_detail);
    }

    @Override
    public void onViewCreated(@NonNull android.view.View view, @Nullable android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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
        clearToolbarNewsDetail();
        if (newsId != null) {
            NewsDetailViewModel vm = new ViewModelProvider(this, new NewsDetailViewModelFactory()).get(NewsDetailViewModel.class);
            vm.loadNews(newsId);
        }
    }

    @Override
    public void onPause() {
        clearToolbarNewsDetail();
        super.onPause();
    }

    @Nullable
    private static String trimmedOrNull(@Nullable String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @Nullable
    private static String formatGroupSectionMeta(@Nullable String group, @Nullable String section) {
        String g = trimmedOrNull(group);
        String sec = trimmedOrNull(section);
        if (g != null && sec != null) {
            return g + " · " + sec;
        }
        if (g != null) {
            return g;
        }
        return sec;
    }

    private void applyToolbarNewsDetailTime(@Nullable String timeStr) {
        TextView tv = requireActivity().findViewById(R.id.tvToolbarNewsDetailTime);
        if (tv == null) {
            return;
        }
        if (timeStr == null || timeStr.isEmpty()) {
            tv.setVisibility(View.GONE);
            tv.setText("");
            tv.setContentDescription(null);
            return;
        }
        tv.setText(timeStr);
        tv.setVisibility(View.VISIBLE);
        tv.setContentDescription(timeStr);
    }

    private void applyToolbarNewsDetailMeta(@Nullable String group, @Nullable String section) {
        TextView tv = requireActivity().findViewById(R.id.tvToolbarNewsDetailMeta);
        if (tv == null) {
            return;
        }
        String meta = formatGroupSectionMeta(group, section);
        if (meta == null) {
            tv.setVisibility(View.GONE);
            tv.setText("");
            tv.setContentDescription(null);
            return;
        }
        tv.setText(meta);
        tv.setVisibility(View.VISIBLE);
        tv.setContentDescription(meta);
    }

    private void clearToolbarNewsDetail() {
        android.app.Activity a = getActivity();
        if (a == null) {
            return;
        }
        TextView timeTv = a.findViewById(R.id.tvToolbarNewsDetailTime);
        if (timeTv != null) {
            timeTv.setVisibility(View.GONE);
            timeTv.setText("");
            timeTv.setContentDescription(null);
        }
        TextView metaTv = a.findViewById(R.id.tvToolbarNewsDetailMeta);
        if (metaTv != null) {
            metaTv.setVisibility(View.GONE);
            metaTv.setText("");
            metaTv.setContentDescription(null);
        }
    }

    private void onLoadResult(@Nullable Result<NewsDetailsResponse> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
            NewsDetailsResponse data = ((Result.Success<NewsDetailsResponse>) result).data;
            String timeStr = NewsDateTime.format(data.time);
            if (isAdded()) {
                applyToolbarNewsDetailTime(timeStr);
                applyToolbarNewsDetailMeta(data.group, data.section);
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
