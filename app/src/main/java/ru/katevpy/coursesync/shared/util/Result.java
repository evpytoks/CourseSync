package ru.katevpy.coursesync.shared.util;

import androidx.annotation.Nullable;

import ru.katevpy.coursesync.shared.dto.ApiError;

public abstract class Result<T> {
    private Result() {}

    public static final class Success<T> extends Result<T> {
        public final T data;
        public Success(T data) { this.data = data; }
    }

    public static final class HttpError<T> extends Result<T> {
        public final int httpCode;
        @Nullable public final ApiError error;
        public HttpError(int httpCode, @Nullable ApiError error) {
            this.httpCode = httpCode;
            this.error = error;
        }
    }

    public static final class NetworkError<T> extends Result<T> {
        public final Throwable t;
        public NetworkError(Throwable t) { this.t = t; }
    }

    public static final class LogicalError<T> extends Result<T> {
        public final String message;
        public LogicalError(String message) { this.message = message; }
    }

    public static <T> Result<T> success(T data) { return new Success<>(data); }
    public static <T> Result<T> httpError(int code, @Nullable ApiError error) { return new HttpError<>(code, error); }
    public static <T> Result<T> networkError(Throwable t) { return new NetworkError<>(t); }
    public static <T> Result<T> logicalError(String msg) { return new LogicalError<>(msg); }
}
