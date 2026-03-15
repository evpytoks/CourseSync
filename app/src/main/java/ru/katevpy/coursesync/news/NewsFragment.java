package ru.katevpy.coursesync.news;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.shared.SharedGroupViewModel;
import ru.katevpy.coursesync.shared.dto.NewsListItem;
import ru.katevpy.coursesync.shared.util.Result;

public class NewsFragment extends Fragment {

    private NewsViewModel viewModel;
    private LinearLayout newsList;

    public NewsFragment() {
        super(R.layout.fragment_news);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View noGroupMessage = view.findViewById(R.id.newsNoGroupMessage);
        View newsContent = view.findViewById(R.id.newsContent);
        newsList = view.findViewById(R.id.newsList);

        viewModel = new ViewModelProvider(this, new NewsViewModelFactory()).get(NewsViewModel.class);

        SharedGroupViewModel groupVm = new ViewModelProvider(requireActivity()).get(SharedGroupViewModel.class);
        groupVm.getGroupState().observe(getViewLifecycleOwner(), state -> {
            if (state != null && state.hasGroup()) {
                noGroupMessage.setVisibility(View.GONE);
                newsContent.setVisibility(View.VISIBLE);
                viewModel.loadNews();
            } else {
                noGroupMessage.setVisibility(View.VISIBLE);
                newsContent.setVisibility(View.GONE);
            }
        });

        viewModel.getLoadResult().observe(getViewLifecycleOwner(), this::onLoadResult);
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedGroupViewModel groupVm = new ViewModelProvider(requireActivity()).get(SharedGroupViewModel.class);
        if (groupVm.getGroupState().getValue() != null && groupVm.getGroupState().getValue().hasGroup() && newsList != null) {
            viewModel.loadNews();
        }
    }

    private void onLoadResult(@Nullable Result<List<NewsListItem>> result) {
        if (result == null) return;
        if (result instanceof Result.Success) {
            List<NewsListItem> items = ((Result.Success<List<NewsListItem>>) result).data;
            newsList.removeAllViews();
            if (items == null) return;
            int marginBottom = (int) (12 * getResources().getDisplayMetrics().density);
            for (NewsListItem item : items) {
                MaterialButton btn = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                btn.setText(item.name != null ? item.name : "");
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = marginBottom;
                btn.setLayoutParams(lp);
                java.util.UUID newsId = item.id;
                if (newsId != null) {
                    btn.setOnClickListener(v -> {
                        Bundle args = new Bundle();
                        args.putString("newsId", newsId.toString());
                        NavHostFragment.findNavController(this).navigate(R.id.action_newsFragment_to_newsDetailFragment, args);
                    });
                }
                newsList.addView(btn);
            }
            return;
        }
        if (result instanceof Result.HttpError) {
            int code = ((Result.HttpError<List<NewsListItem>>) result).httpCode;
            if (code == 401) {
                App.getDeps().tokenStorage.clear();
                NavHostFragment.findNavController(this).navigate(R.id.loginFragment);
                return;
            }
            if (code == 500) {
                Snackbar.make(requireView(), R.string.news_load_error, Snackbar.LENGTH_SHORT).show();
                return;
            }
        }
        Snackbar.make(requireView(), R.string.internal_error, Snackbar.LENGTH_SHORT).show();
    }
}
