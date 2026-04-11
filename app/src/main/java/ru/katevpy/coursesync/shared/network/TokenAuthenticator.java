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
            try {
                retrofit2.Response<RefreshResponse> r = call.execute();
                if (r.isSuccessful()) {
                    RefreshResponse body = r.body();
                    if (body == null || body.token == null || body.token.isEmpty()
                            || body.refreshToken == null || body.refreshToken.isEmpty()) {
                        return null;
                    }
                    tokenStorage.save(body.token, body.refreshToken);
                    return response.request().newBuilder()
                            .header("Authorization", "Bearer " + body.token)
                            .build();
                }
                int code = r.code();
                if (code == 400 || code == 401 || code == 403) {
                    tokenStorage.clear();
                }
                return null;
            } catch (IOException e) {
                return null;
            }
        }
    }

    private int count(Response r) {
        int c = 1;
        while ((r = r.priorResponse()) != null) c++;
        return c;
    }
}
