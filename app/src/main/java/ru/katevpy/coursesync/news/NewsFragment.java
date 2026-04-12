package ru.katevpy.coursesync.news;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.MainActivity;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.SharedGroupViewModel;
import ru.katevpy.coursesync.shared.dto.NewsListItem;
import ru.katevpy.coursesync.shared.util.Result;
import ru.katevpy.coursesync.ui.ErrorUi;
import ru.katevpy.coursesync.ui.ListSpacingDecoration;

public class NewsFragment extends Fragment {

    private NewsViewModel viewModel;
    private RecyclerView newsRecycler;
    private View errorBanner;
    private View newsEmptyText;
    private NewsListAdapter listAdapter;

    public NewsFragment() {
        super(R.layout.fragment_news);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        newsRecycler = view.findViewById(R.id.newsRecycler);
        errorBanner = view.findViewById(R.id.errorBanner);
        newsEmptyText = view.findViewById(R.id.newsEmptyText);

        newsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        int spacing = getResources().getDimensionPixelSize(R.dimen.grid_1);
        newsRecycler.addItemDecoration(new ListSpacingDecoration(spacing));

        listAdapter = new NewsListAdapter(newsId -> {
            Bundle args = new Bundle();
            args.putString("newsId", newsId.toString());
            NavHostFragment.findNavController(this).navigate(R.id.action_newsFragment_to_newsDetailFragment, args);
        });
        newsRecycler.setAdapter(listAdapter);

        viewModel = new ViewModelProvider(requireActivity(), new NewsViewModelFactory()).get(NewsViewModel.class);

        SharedGroupViewModel groupVm = new ViewModelProvider(requireActivity()).get(SharedGroupViewModel.class);
        groupVm.getGroupState().observe(getViewLifecycleOwner(), state -> viewModel.loadNews());

        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);
        viewModel.getMarkAllReadError().observe(getViewLifecycleOwner(), this::onMarkAllReadError);
        viewModel.getUnreadCount().observe(getViewLifecycleOwner(), count -> {
            if (count == null) {
                return;
            }
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).updateNewsUnreadBadge(count);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (newsRecycler != null) {
            viewModel.loadNews();
        }
    }

    private void onLoadResult(@Nullable Result<List<NewsListItem>> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
            ErrorUi.hideErrorBanner(errorBanner);
            List<NewsListItem> items = ((Result.Success<List<NewsListItem>>) result).data;
            List<NewsListItem> list = items != null ? items : Collections.emptyList();
            listAdapter.submitList(list);
            boolean empty = list.isEmpty();
            newsEmptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
            newsRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
            return;
        }
        if (result instanceof Result.HttpError) {
            Result.HttpError<List<NewsListItem>> he = (Result.HttpError<List<NewsListItem>>) result;
            int code = he.httpCode;
            if (code == 401) {
                ErrorUi.hideErrorBanner(errorBanner);
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            if (code == 400 && isNoGroupSelected(he)) {
                ErrorUi.hideErrorBanner(errorBanner);
                listAdapter.submitList(Collections.emptyList());
                newsEmptyText.setVisibility(View.GONE);
                newsRecycler.setVisibility(View.VISIBLE);
                return;
            }
            if (code == 500) {
                listAdapter.submitList(null);
                newsEmptyText.setVisibility(View.GONE);
                newsRecycler.setVisibility(View.VISIBLE);
                ErrorUi.showErrorBanner(errorBanner, R.string.news_load_error, () -> viewModel.loadNews());
                return;
            }
        }
        listAdapter.submitList(null);
        newsEmptyText.setVisibility(View.GONE);
        newsRecycler.setVisibility(View.VISIBLE);
        ErrorUi.showErrorBanner(errorBanner, R.string.internal_error, () -> viewModel.loadNews());
    }

    private static boolean isNoGroupSelected(Result.HttpError<List<NewsListItem>> he) {
        if (he.error == null || he.error.code == null) {
            return false;
        }
        return "no_group_selected".equals(he.error.code);
    }

    private void onMarkAllReadError(@Nullable Result<Void> result) {
        if (result == null) {
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<Void>) result).httpCode;
            if (code == 401) {
                viewModel.consumeMarkAllReadError();
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            ErrorUi.show(this, R.string.news_mark_all_read_error, ErrorUi.Duration.SHORT);
            viewModel.consumeMarkAllReadError();
            return;
        }
        if (result instanceof Result.NetworkError) {
            ErrorUi.show(this, R.string.news_mark_all_read_error, ErrorUi.Duration.SHORT);
            viewModel.consumeMarkAllReadError();
            return;
        }
        ErrorUi.show(this, R.string.news_mark_all_read_error, ErrorUi.Duration.SHORT);
        viewModel.consumeMarkAllReadError();
    }
}
