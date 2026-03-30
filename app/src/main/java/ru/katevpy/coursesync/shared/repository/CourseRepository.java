package ru.katevpy.coursesync.shared.repository;

import com.google.gson.Gson;

import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;

import ru.katevpy.coursesync.shared.dto.AddCourseRequest;
import ru.katevpy.coursesync.shared.dto.ApiError;
import ru.katevpy.coursesync.shared.dto.CourseDetailsResponse;
import ru.katevpy.coursesync.shared.dto.CourseGradingElementItem;
import ru.katevpy.coursesync.shared.dto.CourseGradingElementsResponse;
import ru.katevpy.coursesync.shared.dto.CourseGradingScoresResponse;
import ru.katevpy.coursesync.shared.dto.CourseGradingElementRequest;
import ru.katevpy.coursesync.shared.dto.CourseGradingTextResponse;
import ru.katevpy.coursesync.shared.dto.CourseListResponse;
import ru.katevpy.coursesync.shared.dto.CoursePersonalMaterialListItem;
import ru.katevpy.coursesync.shared.dto.CoursePersonalMaterialListResponse;
import ru.katevpy.coursesync.shared.dto.CourseMaterialListItem;
import ru.katevpy.coursesync.shared.dto.CourseMaterialListResponse;
import ru.katevpy.coursesync.shared.dto.ErrorEnvelope;
import ru.katevpy.coursesync.shared.dto.SaveCourseGradingRequest;
import ru.katevpy.coursesync.shared.dto.UpdateCourseGradingScoresRequest;
import ru.katevpy.coursesync.shared.network.CourseApi;
import ru.katevpy.coursesync.shared.util.Result;

public class CourseRepository {

    private final CourseApi api;
    private final Gson gson = new Gson();

    public CourseRepository(CourseApi api) {
        this.api = api;
    }

    private ApiError parseError(ResponseBody body) {
        if (body == null) return null;
        try {
            ErrorEnvelope env = gson.fromJson(body.string(), ErrorEnvelope.class);
            return env != null ? env.error : null;
        } catch (Exception e) {
            return null;
        }
    }

    public Result<CourseListResponse> getCourseList() {
        try {
            Response<CourseListResponse> r = api.listCourses().execute();
            if (r.isSuccessful() && r.body() != null) {
                return Result.success(r.body());
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<CourseDetailsResponse> getCourse(UUID courseId) {
        try {
            Response<CourseDetailsResponse> r = api.getCourse(courseId).execute();
            if (r.isSuccessful() && r.body() != null) {
                return Result.success(r.body());
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<CourseGradingTextResponse> getGradingText(UUID courseId) {
        try {
            Response<CourseGradingTextResponse> r = api.getGradingText(courseId).execute();
            if (r.isSuccessful() && r.body() != null) {
                return Result.success(r.body());
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<CourseGradingElementsResponse> getGrading(UUID courseId) {
        try {
            Response<CourseGradingElementsResponse> r = api.getGrading(courseId).execute();
            if (r.isSuccessful() && r.body() != null) {
                return Result.success(r.body());
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<CourseGradingScoresResponse> getGradingScores(UUID courseId, String elementName) {
        String q = elementName != null ? elementName.trim() : "";
        try {
            Response<CourseGradingScoresResponse> r = api.getGradingScores(courseId, q).execute();
            if (r.isSuccessful() && r.body() != null) {
                return Result.success(r.body());
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<Void> updateGradingScores(UUID courseId, String elementName, List<Double> scores) {
        try {
            Response<Void> r = api.updateGradingScores(
                    courseId,
                    new UpdateCourseGradingScoresRequest(elementName, scores)).execute();
            if (r.isSuccessful()) {
                return Result.success(null);
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<Void> saveGrading(UUID courseId, String text, List<CourseGradingElementItem> elements) {
        List<CourseGradingElementRequest> reqElements = new java.util.ArrayList<>();
        if (elements != null) {
            for (CourseGradingElementItem e : elements) {
                String name = e != null && e.name != null ? e.name : "";
                Double c = e != null ? e.coefficient : null;
                reqElements.add(new CourseGradingElementRequest(name, c != null ? c : 0.0));
            }
        }
        try {
            Response<Void> r = api.saveGrading(courseId, new SaveCourseGradingRequest(text, reqElements)).execute();
            if (r.isSuccessful()) {
                return Result.success(null);
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            if (e instanceof EOFException) {
                return Result.success(null);
            }
            return Result.networkError(e);
        }
    }

    public Result<List<CourseMaterialListItem>> listGeneralMaterials(UUID courseId) {
        try {
            Response<CourseMaterialListResponse> r = api.listGeneralMaterials(courseId).execute();
            if (r.isSuccessful() && r.body() != null) {
                List<CourseMaterialListItem> list = r.body().materials;
                return Result.success(list != null ? list : Collections.emptyList());
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<File> downloadGeneralMaterialPdfToFile(UUID courseId, UUID materialId, File destination) {
        try {
            Response<ResponseBody> r = api.downloadGeneralMaterialPdf(courseId, materialId).execute();
            if (!r.isSuccessful()) {
                return Result.httpError(r.code(), parseError(r.errorBody()));
            }
            ResponseBody body = r.body();
            if (body == null) {
                return Result.networkError(new IOException("empty body"));
            }
            try (InputStream in = body.byteStream(); FileOutputStream out = new FileOutputStream(destination)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            }
            return Result.success(destination);
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<Void> addGeneralMaterial(UUID courseId, byte[] fileBytes, String fileName) {
        String name = (fileName != null && !fileName.isEmpty()) ? fileName : "document.pdf";
        RequestBody body = RequestBody.create(fileBytes, MediaType.parse("application/pdf"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", name, body);
        try {
            Response<Void> r = api.addGeneralMaterial(courseId, part).execute();
            if (r.isSuccessful()) {
                return Result.success(null);
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            if (e instanceof EOFException) {
                return Result.success(null);
            }
            return Result.networkError(e);
        }
    }

    public Result<Void> deleteGeneralMaterial(UUID courseId, UUID materialId) {
        try {
            Response<Void> r = api.deleteGeneralMaterial(courseId, materialId).execute();
            if (r.isSuccessful()) {
                return Result.success(null);
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            if (e instanceof EOFException) {
                return Result.success(null);
            }
            return Result.networkError(e);
        }
    }

    public Result<List<CoursePersonalMaterialListItem>> listPersonalMaterials(UUID courseId) {
        try {
            Response<CoursePersonalMaterialListResponse> r = api.listPersonalMaterials(courseId).execute();
            if (r.isSuccessful() && r.body() != null) {
                List<CoursePersonalMaterialListItem> list = r.body().materials;
                return Result.success(list != null ? list : Collections.emptyList());
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<Void> deletePersonalMaterial(UUID courseId, UUID materialId) {
        try {
            Response<Void> r = api.deletePersonalMaterial(courseId, materialId).execute();
            if (r.isSuccessful()) {
                return Result.success(null);
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            if (e instanceof EOFException) {
                return Result.success(null);
            }
            return Result.networkError(e);
        }
    }

    public Result<File> downloadPersonalMaterialPdfToFile(UUID courseId, UUID materialId, File destination) {
        try {
            Response<ResponseBody> r = api.downloadPersonalMaterialPdf(courseId, materialId).execute();
            if (!r.isSuccessful()) {
                return Result.httpError(r.code(), parseError(r.errorBody()));
            }
            ResponseBody body = r.body();
            if (body == null) {
                return Result.networkError(new IOException("empty body"));
            }
            try (InputStream in = body.byteStream(); FileOutputStream out = new FileOutputStream(destination)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            }
            return Result.success(destination);
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<Void> addPersonalMaterial(UUID courseId, byte[] fileBytes, String fileName) {
        String name = (fileName != null && !fileName.isEmpty()) ? fileName : "document.pdf";
        RequestBody body = RequestBody.create(fileBytes, MediaType.parse("application/pdf"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", name, body);
        try {
            Response<Void> r = api.addPersonalMaterial(courseId, part).execute();
            if (r.isSuccessful()) {
                return Result.success(null);
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            if (e instanceof EOFException) {
                return Result.success(null);
            }
            return Result.networkError(e);
        }
    }

    public Result<Void> updateCourse(UUID courseId, String name, String generalInfo, String usefulLinks) {
        try {
            AddCourseRequest req = new AddCourseRequest(
                    name,
                    generalInfo != null ? generalInfo : "",
                    usefulLinks != null ? usefulLinks : "");
            Response<Void> r = api.changeCourse(courseId, req).execute();
            if (r.isSuccessful()) {
                return Result.success(null);
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            if (e instanceof EOFException) {
                return Result.success(null);
            }
            return Result.networkError(e);
        }
    }

    public Result<Void> createCourse(String name, String generalInfo, String usefulLinks) {
        try {
            AddCourseRequest req = new AddCourseRequest(
                    name,
                    generalInfo != null ? generalInfo : "",
                    usefulLinks != null ? usefulLinks : "");
            Response<Void> r = api.addCourse(req).execute();
            if (r.isSuccessful()) {
                return Result.success(null);
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            if (e instanceof EOFException) {
                return Result.success(null);
            }
            return Result.networkError(e);
        }
    }
}
