package ru.katevpy.coursesync.ui;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import ru.katevpy.coursesync.R;

public final class ErrorUi {

    public enum Duration {
        SHORT,
        LONG
    }

    private ErrorUi() {}

    public static void show(@NonNull Fragment fragment, @StringRes int messageRes, @NonNull Duration duration) {
        Snackbar sb = Snackbar.make(
                fragment.requireView(),
                messageRes,
                duration == Duration.SHORT ? Snackbar.LENGTH_SHORT : Snackbar.LENGTH_LONG);
        applyAnchor(fragment.requireActivity(), sb);
        sb.show();
    }

    public static void show(@NonNull Fragment fragment, @NonNull CharSequence message, @NonNull Duration duration) {
        Snackbar sb = Snackbar.make(
                fragment.requireView(),
                message,
                duration == Duration.SHORT ? Snackbar.LENGTH_SHORT : Snackbar.LENGTH_LONG);
        applyAnchor(fragment.requireActivity(), sb);
        sb.show();
    }

    public static void show(@NonNull Activity activity, @NonNull View rootView, @StringRes int messageRes, @NonNull Duration duration) {
        Snackbar sb = Snackbar.make(
                rootView,
                messageRes,
                duration == Duration.SHORT ? Snackbar.LENGTH_SHORT : Snackbar.LENGTH_LONG);
        applyAnchor(activity, sb);
        sb.show();
    }

    private static void applyAnchor(@NonNull Activity activity, @NonNull Snackbar snackbar) {
        View anchor = activity.findViewById(R.id.bottom_nav);
        if (anchor != null && anchor.getVisibility() == View.VISIBLE) {
            snackbar.setAnchorView(anchor);
        }
    }

    public static void showErrorBanner(@NonNull View bannerRoot, @StringRes int messageRes, @Nullable Runnable onRetry) {
        TextView msg = bannerRoot.findViewById(R.id.errorBannerMessage);
        MaterialButton retry = bannerRoot.findViewById(R.id.errorBannerRetry);
        msg.setText(messageRes);
        bannerRoot.setVisibility(View.VISIBLE);
        if (onRetry != null) {
            retry.setVisibility(View.VISIBLE);
            retry.setOnClickListener(v -> onRetry.run());
        } else {
            retry.setVisibility(View.GONE);
            retry.setOnClickListener(null);
        }
    }

    public static void showErrorBanner(@NonNull View bannerRoot, @NonNull CharSequence message, @Nullable Runnable onRetry) {
        TextView msg = bannerRoot.findViewById(R.id.errorBannerMessage);
        MaterialButton retry = bannerRoot.findViewById(R.id.errorBannerRetry);
        msg.setText(message);
        bannerRoot.setVisibility(View.VISIBLE);
        if (onRetry != null) {
            retry.setVisibility(View.VISIBLE);
            retry.setOnClickListener(v -> onRetry.run());
        } else {
            retry.setVisibility(View.GONE);
            retry.setOnClickListener(null);
        }
    }

    public static void hideErrorBanner(@NonNull View bannerRoot) {
        bannerRoot.setVisibility(View.GONE);
        MaterialButton retry = bannerRoot.findViewById(R.id.errorBannerRetry);
        retry.setOnClickListener(null);
    }
}
