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
import ru.katevpy.coursesync.shared.dto.NewsUnreadCountResponse;

public interface NewsApi {

    @GET("news")
    Call<NewsListResponse> listNews();

    @GET("news/unread-count")
    Call<NewsUnreadCountResponse> unreadCount();

    @GET("news/{id}")
    Call<NewsDetailsResponse> getNews(@Path("id") UUID id);

    @POST("news")
    Call<Void> addNews(@Body AddNewsRequest request);
}
