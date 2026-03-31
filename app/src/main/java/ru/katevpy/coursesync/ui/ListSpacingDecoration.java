package ru.katevpy.coursesync.ui;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public final class ListSpacingDecoration extends RecyclerView.ItemDecoration {

    private final int spacingPx;

    public ListSpacingDecoration(int spacingPx) {
        this.spacingPx = spacingPx;
    }

    @Override
    public void getItemOffsets(
            @NonNull Rect outRect,
            @NonNull View view,
            @NonNull RecyclerView parent,
            @NonNull RecyclerView.State state
    ) {
        int position = parent.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        int count = state.getItemCount();
        if (position < count - 1) {
            outRect.bottom = spacingPx;
        }
    }
}
