package ru.katevpy.coursesync.shared.network;

import java.util.UUID;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.DELETE;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Streaming;

import ru.katevpy.coursesync.shared.dto.AddCourseRequest;
import ru.katevpy.coursesync.shared.dto.CourseDetailsResponse;
import ru.katevpy.coursesync.shared.dto.CourseListResponse;
import ru.katevpy.coursesync.shared.dto.CourseMaterialListResponse;

public interface CourseApi {

    @GET("course/list")
    Call<CourseListResponse> listCourses();

    @GET("course/{id}")
    Call<CourseDetailsResponse> getCourse(@Path("id") UUID id);

    @GET("course/{id}/general_materials")
    Call<CourseMaterialListResponse> listGeneralMaterials(@Path("id") UUID id);

    @Streaming
    @GET("course/{courseId}/general_materials/{materialId}/pdf")
    Call<ResponseBody> downloadGeneralMaterialPdf(
            @Path("courseId") UUID courseId,
            @Path("materialId") UUID materialId);

    @Multipart
    @POST("course/{id}/general_materials/add")
    Call<Void> addGeneralMaterial(@Path("id") UUID id, @Part MultipartBody.Part file);

    @DELETE("course/{id}/general_materials/{materialId}")
    Call<Void> deleteGeneralMaterial(@Path("id") UUID id, @Path("materialId") UUID materialId);

    @GET("course/{id}/personal_materials")
    Call<CourseMaterialListResponse> listPersonalMaterials(@Path("id") UUID id);

    @Streaming
    @GET("course/{courseId}/personal_materials/{materialId}/pdf")
    Call<ResponseBody> downloadPersonalMaterialPdf(
            @Path("courseId") UUID courseId,
            @Path("materialId") UUID materialId);

    @Multipart
    @POST("course/{id}/personal_materials/add")
    Call<Void> addPersonalMaterial(@Path("id") UUID id, @Part MultipartBody.Part file);

    @POST("course/add")
    Call<Void> addCourse(@Body AddCourseRequest request);

    @PUT("course/{id}/change")
    Call<Void> changeCourse(@Path("id") UUID id, @Body AddCourseRequest request);
}
