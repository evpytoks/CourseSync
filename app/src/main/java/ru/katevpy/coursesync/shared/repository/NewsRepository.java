package ru.katevpy.coursesync.shared.repository;

import com.google.gson.Gson;

import java.io.EOFException;
import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Response;

import java.util.UUID;

import ru.katevpy.coursesync.shared.dto.AddNewsRequest;
import ru.katevpy.coursesync.shared.dto.ApiError;
import ru.katevpy.coursesync.shared.dto.ErrorEnvelope;
import ru.katevpy.coursesync.shared.dto.NewsDetailsResponse;
import ru.katevpy.coursesync.shared.dto.NewsListResponse;
import ru.katevpy.coursesync.shared.dto.NewsUnreadCountResponse;
import ru.katevpy.coursesync.shared.network.NewsApi;
import ru.katevpy.coursesync.shared.util.Result;

public class NewsRepository {

    private final NewsApi api;
    private final Gson gson = new Gson();

    public NewsRepository(NewsApi api) {
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

    public Result<NewsListResponse> getNewsList() {
        try {
            Response<NewsListResponse> r = api.listNews().execute();
            if (r.isSuccessful() && r.body() != null) {
                return Result.success(r.body());
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<NewsUnreadCountResponse> getUnreadCount() {
        try {
            Response<NewsUnreadCountResponse> r = api.unreadCount().execute();
            if (r.isSuccessful() && r.body() != null) {
                return Result.success(r.body());
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<NewsDetailsResponse> getNews(UUID id) {
        try {
            Response<NewsDetailsResponse> r = api.getNews(id).execute();
            if (r.isSuccessful() && r.body() != null) {
                return Result.success(r.body());
            }
            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<Void> addNews(UUID groupId, String text) {
        try {
            AddNewsRequest req = new AddNewsRequest(groupId, text);
            Response<Void> r = api.addNews(req).execute();
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
