package ru.katevpy.coursesync.courses;

import android.app.Application;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.shared.dto.CourseMaterialListItem;
import ru.katevpy.coursesync.shared.repository.CourseRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class CoursePersonalMaterialsViewModel extends AndroidViewModel {

    private static final int MAX_PDF_BYTES = 30 * 1024 * 1024;

    private final CourseRepository repo = new CourseRepository(App.getDeps().courseApi);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result<List<CourseMaterialListItem>>> loadResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Void>> uploadResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> uploadInProgress = new MutableLiveData<>(false);

    public CoursePersonalMaterialsViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<Result<List<CourseMaterialListItem>>> getLoadResult() {
        return loadResult;
    }

    public LiveData<Result<Void>> getUploadResult() {
        return uploadResult;
    }

    public LiveData<Boolean> getUploadInProgress() {
        return uploadInProgress;
    }

    public void loadPersonalMaterials(UUID courseId) {
        io.execute(() -> loadResult.postValue(repo.listPersonalMaterials(courseId)));
    }

    public void uploadPersonalMaterial(UUID courseId, Uri uri) {
        io.execute(() -> {
            uploadInProgress.postValue(true);
            try {
                ContentResolver cr = getApplication().getContentResolver();
                try (InputStream in = cr.openInputStream(uri)) {
                    if (in == null) {
                        uploadResult.postValue(Result.logicalError("open_failed"));
                        return;
                    }
                    byte[] data = readPdfLimited(in, MAX_PDF_BYTES);
                    if (data == null) {
                        uploadResult.postValue(Result.logicalError("file_too_large"));
                        return;
                    }
                    if (data.length == 0) {
                        uploadResult.postValue(Result.logicalError("file_required"));
                        return;
                    }
                    if (!isPdfHeader(data)) {
                        uploadResult.postValue(Result.logicalError("not_pdf"));
                        return;
                    }
                    String name = resolveDisplayName(cr, uri);
                    uploadResult.postValue(repo.addPersonalMaterial(courseId, data, name));
                }
            } catch (IOException e) {
                uploadResult.postValue(Result.networkError(e));
            } finally {
                uploadInProgress.postValue(false);
            }
        });
    }

    private static byte[] readPdfLimited(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(chunk)) != -1) {
            int next = total + n;
            if (next > maxBytes) {
                return null;
            }
            buf.write(chunk, 0, n);
            total = next;
        }
        return buf.toByteArray();
    }

    private static boolean isPdfHeader(byte[] data) {
        return data.length >= 4
                && data[0] == '%'
                && data[1] == 'P'
                && data[2] == 'D'
                && data[3] == 'F';
    }

    private static String resolveDisplayName(ContentResolver cr, Uri uri) {
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) {
                    String n = c.getString(i);
                    if (n != null && !n.isEmpty()) {
                        return n;
                    }
                }
            }
        }
        return "document.pdf";
    }
}
