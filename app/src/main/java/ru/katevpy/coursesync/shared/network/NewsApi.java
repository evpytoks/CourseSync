package ru.katevpy.coursesync.shared.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

import java.util.UUID;

import ru.katevpy.coursesync.shared.dto.AddNewsRequest;
import ru.katevpy.coursesync.shared.dto.NewsDetailsResponse;
import ru.katevpy.coursesync.shared.dto.NewsListResponse;

public interface NewsApi {

    @GET("news")
    Call<NewsListResponse> listNews();

    @GET("news/{id}")
    Call<NewsDetailsResponse> getNews(@Path("id") UUID id);

    @POST("news/add")
    Call<Void> addNews(@Body AddNewsRequest request);
}
