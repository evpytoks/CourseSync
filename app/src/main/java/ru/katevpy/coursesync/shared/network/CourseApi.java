package ru.katevpy.coursesync.shared.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

import ru.katevpy.coursesync.shared.dto.AddCourseRequest;
import ru.katevpy.coursesync.shared.dto.CourseListResponse;

public interface CourseApi {

    @GET("course/list")
    Call<CourseListResponse> listCourses();

    @POST("course/add")
    Call<Void> addCourse(@Body AddCourseRequest request);
}
