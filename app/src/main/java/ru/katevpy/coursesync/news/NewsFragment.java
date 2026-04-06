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
    private NewsListAdapter listAdapter;

    public NewsFragment() {
        super(R.layout.fragment_news);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        newsRecycler = view.findViewById(R.id.newsRecycler);
        errorBanner = view.findViewById(R.id.errorBanner);

        newsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        int spacing = getResources().getDimensionPixelSize(R.dimen.grid_1);
        newsRecycler.addItemDecoration(new ListSpacingDecoration(spacing));

        listAdapter = new NewsListAdapter(newsId -> {
            Bundle args = new Bundle();
            args.putString("newsId", newsId.toString());
            NavHostFragment.findNavController(this).navigate(R.id.action_newsFragment_to_newsDetailFragment, args);
        });
        newsRecycler.setAdapter(listAdapter);

        viewModel = new ViewModelProvider(this, new NewsViewModelFactory()).get(NewsViewModel.class);

        SharedGroupViewModel groupVm = new ViewModelProvider(requireActivity()).get(SharedGroupViewModel.class);
        groupVm.getGroupState().observe(getViewLifecycleOwner(), state -> viewModel.loadNews());

        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);
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
            listAdapter.submitList(items);
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
                return;
            }
            if (code == 500) {
                listAdapter.submitList(null);
                ErrorUi.showErrorBanner(errorBanner, R.string.news_load_error, () -> viewModel.loadNews());
                return;
            }
        }
        listAdapter.submitList(null);
        ErrorUi.showErrorBanner(errorBanner, R.string.internal_error, () -> viewModel.loadNews());
    }

    private static boolean isNoGroupSelected(Result.HttpError<List<NewsListItem>> he) {
        if (he.error == null || he.error.code == null) {
            return false;
        }
        return "no_group_selected".equals(he.error.code);
    }
}
