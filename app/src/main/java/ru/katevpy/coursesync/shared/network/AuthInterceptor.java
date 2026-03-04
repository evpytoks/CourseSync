package ru.katevpy.coursesync.shared.network;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import ru.katevpy.coursesync.shared.storage.TokenStorage;

public final class AuthInterceptor implements Interceptor {
    private final TokenStorage tokenStorage;

    public AuthInterceptor(TokenStorage tokenStorage) {
        this.tokenStorage = tokenStorage;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        String path = original.url().encodedPath();

        if (path.startsWith("/auth/")) {
            return chain.proceed(original);
        }

        String access = tokenStorage.getAccess();
        if (access == null || access.isEmpty()) {
            return chain.proceed(original);
        }

        return chain.proceed(
                original.newBuilder()
                        .header("Authorization", "Bearer " + access)
                        .build()
        );
    }
}
