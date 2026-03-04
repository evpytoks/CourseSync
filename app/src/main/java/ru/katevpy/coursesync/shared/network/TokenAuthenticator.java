package ru.katevpy.coursesync.shared.network;

import androidx.annotation.Nullable;
import java.io.IOException;
import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import retrofit2.Call;
import ru.katevpy.coursesync.shared.storage.TokenStorage;
import ru.katevpy.coursesync.shared.dto.RefreshResponse;
import ru.katevpy.coursesync.shared.dto.RefreshRequest;

public final class TokenAuthenticator implements Authenticator {

    private final TokenStorage tokenStorage;
    private final AuthApi refreshApi;
    private final Object lock = new Object();

    public TokenAuthenticator(TokenStorage tokenStorage, AuthApi refreshApi) {
        this.tokenStorage = tokenStorage;
        this.refreshApi = refreshApi;
    }

    @Nullable
    @Override
    public Request authenticate(Route route, Response response) throws IOException {
        if (count(response) >= 2) {
            tokenStorage.clear();
            return null;
        }

        String path = response.request().url().encodedPath();
        if (path.startsWith("/auth/")) return null;

        synchronized (lock) {
            String refresh = tokenStorage.getRefresh();
            if (refresh == null || refresh.isEmpty()) {
                tokenStorage.clear();
                return null;
            }

            Call<RefreshResponse> call = refreshApi.refresh(new RefreshRequest(refresh));
            retrofit2.Response<RefreshResponse> r = call.execute();

            if (!r.isSuccessful() || r.body() == null) {
                tokenStorage.clear();
                return null;
            }

            RefreshResponse body = r.body();
            tokenStorage.save(body.token, body.refreshToken);

            return response.request().newBuilder()
                    .header("Authorization", "Bearer " + body.token)
                    .build();
        }
    }

    private int count(Response r) {
        int c = 1;
        while ((r = r.priorResponse()) != null) c++;
        return c;
    }
}
