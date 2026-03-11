package ru.katevpy.coursesync.shared.repository;

import com.google.gson.Gson;

import java.io.EOFException;
import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Response;

import ru.katevpy.coursesync.shared.dto.AddCourseRequest;
import ru.katevpy.coursesync.shared.dto.ApiError;
import ru.katevpy.coursesync.shared.dto.CourseListResponse;
import ru.katevpy.coursesync.shared.dto.ErrorEnvelope;
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
