package ru.katevpy.coursesync.courses;

import android.content.Context;
import android.content.res.Resources;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;

import ru.katevpy.coursesync.R;
import ru.katevpy.coursesync.news.NewsDateTime;

public final class MaterialDocumentCardHelper {

    private MaterialDocumentCardHelper() {}

    @NonNull
    private static String formatCreatedAtForDisplay(@Nullable String createdAtRaw) {
        if (createdAtRaw == null || createdAtRaw.isEmpty()) {
            return "";
        }
        String s = createdAtRaw.trim();
        String pretty = NewsDateTime.format(s);
        return pretty != null ? pretty : s;
    }

    public static void styleCard(@NonNull MaterialCardView card, @NonNull Resources res) {
        card.setCardElevation(res.getDimension(R.dimen.material_document_card_elevation));
        int strokePx = res.getDimensionPixelSize(R.dimen.material_document_card_stroke_width);
        card.setStrokeWidth(strokePx);
        card.setStrokeColor(
                MaterialColors.getColor(card, com.google.android.material.R.attr.colorOutlineVariant));
    }

    @NonNull
    public static ShapeableImageView createThumbnail(@NonNull Context context, @NonNull Resources res) {
        ShapeableImageView thumb = new ShapeableImageView(context);
        float corner = res.getDimension(R.dimen.material_pdf_thumb_corner_radius);
        thumb.setShapeAppearanceModel(
                ShapeAppearanceModel.builder()
                        .setAllCorners(CornerFamily.ROUNDED, corner)
                        .build());
        int thumbPx = res.getDimensionPixelSize(R.dimen.material_pdf_thumbnail_size);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(thumbPx, thumbPx);
        lp.setMarginEnd(res.getDimensionPixelSize(R.dimen.grid_2));
        thumb.setLayoutParams(lp);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundColor(
                MaterialColors.getColor(thumb, com.google.android.material.R.attr.colorSurfaceVariant));
        return thumb;
    }

    @NonNull
    public static String formatMetaLine(@Nullable String authorEmail, @Nullable String createdAt) {
        String a = authorEmail != null ? authorEmail.trim() : "";
        String d = formatCreatedAtForDisplay(createdAt);
        if (a.isEmpty() && d.isEmpty()) {
            return "";
        }
        if (a.isEmpty()) {
            return d;
        }
        if (d.isEmpty()) {
            return a;
        }
        return a + " · " + d;
    }
}
