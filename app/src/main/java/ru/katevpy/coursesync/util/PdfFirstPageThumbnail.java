package ru.katevpy.coursesync.util;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;

public final class PdfFirstPageThumbnail {

    private PdfFirstPageThumbnail() {}

    @Nullable
    public static Bitmap renderFirstPage(@NonNull File pdfFile, int maxSidePx) {
        if (maxSidePx < 1) {
            return null;
        }
        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            return null;
        }
        ParcelFileDescriptor pfd = null;
        PdfRenderer renderer = null;
        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);
            if (renderer.getPageCount() <= 0) {
                return null;
            }
            PdfRenderer.Page page = renderer.openPage(0);
            try {
                int pw = page.getWidth();
                int ph = page.getHeight();
                if (pw <= 0 || ph <= 0) {
                    return null;
                }
                float scale = Math.min((float) maxSidePx / pw, (float) maxSidePx / ph);
                int outW = Math.max(1, Math.round(pw * scale));
                int outH = Math.max(1, Math.round(ph * scale));
                Bitmap bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                Matrix matrix = new Matrix();
                matrix.setScale(scale, scale);
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                return bitmap;
            } finally {
                page.close();
            }
        } catch (IOException e) {
            return null;
        } finally {
            if (renderer != null) {
                renderer.close();
            }
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
