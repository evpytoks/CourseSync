package ru.katevpy.coursesync.util;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

public interface MaterialPdfThumbnailCallback {

    void onBitmap(@NonNull Bitmap bitmap);

    void onUnavailable();
}
