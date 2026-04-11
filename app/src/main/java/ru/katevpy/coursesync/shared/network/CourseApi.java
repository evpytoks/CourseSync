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
import retrofit2.http.Query;
import retrofit2.http.Streaming;

import ru.katevpy.coursesync.shared.dto.AddCourseRequest;
import ru.katevpy.coursesync.shared.dto.CalendarListResponse;
import ru.katevpy.coursesync.shared.dto.CourseCumulativeGradeResponse;
import ru.katevpy.coursesync.shared.dto.CourseDetailsResponse;
import ru.katevpy.coursesync.shared.dto.CourseGradingElementListResponse;
import ru.katevpy.coursesync.shared.dto.CourseGradingElementsResponse;
import ru.katevpy.coursesync.shared.dto.CourseGradingScoresResponse;
import ru.katevpy.coursesync.shared.dto.CourseGradingTextResponse;
import ru.katevpy.coursesync.shared.dto.CourseListResponse;
import ru.katevpy.coursesync.shared.dto.CoursePersonalMaterialListResponse;
import ru.katevpy.coursesync.shared.dto.CourseMaterialListResponse;
import ru.katevpy.coursesync.shared.dto.SaveCourseCumulativeGradeRequest;
import ru.katevpy.coursesync.shared.dto.SaveCourseGradingRequest;
import ru.katevpy.coursesync.shared.dto.UpdateCourseGradingScoresRequest;

public interface CourseApi {

    @GET("courses")
    Call<CourseListResponse> listCourses();

    @GET("groups/{groupId}/courses")
    Call<CourseListResponse> listCoursesForGroup(@Path("groupId") UUID groupId);

    @GET("courses/{id}")
    Call<CourseDetailsResponse> getCourse(@Path("id") UUID id);

    @GET("courses/{id}/calendar")
    Call<CalendarListResponse> getCourseCalendar(@Path("id") UUID id);

    @GET("courses/{id}/grading/text")
    Call<CourseGradingTextResponse> getGradingText(@Path("id") UUID id);

    @GET("courses/{id}/grading")
    Call<CourseGradingElementsResponse> getGrading(@Path("id") UUID id);

    @GET("courses/{id}/grading/elements")
    Call<CourseGradingElementListResponse> getGradingElementOptions(@Path("id") UUID id);

    @GET("courses/{id}/cumulative-grades")
    Call<CourseCumulativeGradeResponse> getCumulativeGrade(@Path("id") UUID id);

    @PUT("courses/{id}/cumulative-grades")
    Call<Void> saveCumulativeGrade(
            @Path("id") UUID id,
            @Body SaveCourseCumulativeGradeRequest body);

    @GET("courses/{id}/grading/scores")
    Call<CourseGradingScoresResponse> getGradingScores(
            @Path("id") UUID id,
            @Query("name") String elementName);

    @PUT("courses/{id}/grading/scores")
    Call<Void> updateGradingScores(
            @Path("id") UUID id,
            @Body UpdateCourseGradingScoresRequest body);

    @POST("courses/{id}/grading")
    Call<Void> saveGrading(@Path("id") UUID id, @Body SaveCourseGradingRequest body);

    @GET("courses/{id}/general-materials")
    Call<CourseMaterialListResponse> listGeneralMaterials(@Path("id") UUID id);

    @Streaming
    @GET("courses/{courseId}/general-materials/{materialId}/pdf")
    Call<ResponseBody> downloadGeneralMaterialPdf(
            @Path("courseId") UUID courseId,
            @Path("materialId") UUID materialId);

    @Multipart
    @POST("courses/{id}/general-materials")
    Call<Void> addGeneralMaterial(@Path("id") UUID id, @Part MultipartBody.Part file);

    @DELETE("courses/{id}/general-materials/{materialId}")
    Call<Void> deleteGeneralMaterial(@Path("id") UUID id, @Path("materialId") UUID materialId);

    @GET("courses/{id}/personal-materials")
    Call<CoursePersonalMaterialListResponse> listPersonalMaterials(@Path("id") UUID id);

    @Streaming
    @GET("courses/{courseId}/personal-materials/{materialId}/pdf")
    Call<ResponseBody> downloadPersonalMaterialPdf(
            @Path("courseId") UUID courseId,
            @Path("materialId") UUID materialId);

    @Multipart
    @POST("courses/{id}/personal-materials")
    Call<Void> addPersonalMaterial(@Path("id") UUID id, @Part MultipartBody.Part file);

    @DELETE("courses/{id}/personal-materials/{materialId}")
    Call<Void> deletePersonalMaterial(@Path("id") UUID id, @Path("materialId") UUID materialId);

    @POST("courses")
    Call<Void> addCourse(@Body AddCourseRequest request);

    @PUT("courses/{id}")
    Call<Void> changeCourse(@Path("id") UUID id, @Body AddCourseRequest request);

    @DELETE("courses/{id}")
    Call<Void> deleteCourse(@Path("id") UUID id);
}
