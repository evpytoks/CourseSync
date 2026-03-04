package ru.katevpy.coursesync.shared.network;

import android.content.Context;
import android.content.SharedPreferences;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import ru.katevpy.coursesync.shared.storage.PendingLoginStorage;
import ru.katevpy.coursesync.shared.storage.TokenStorage;

public final class NetworkModule {

    public static final class Deps {
        public final AuthApi authApi;
        public final TokenStorage tokenStorage;
        public final PendingLoginStorage pendingLoginStorage;

        Deps(AuthApi authApi, TokenStorage tokenStorage, PendingLoginStorage pendingLoginStorage) {
            this.authApi = authApi;
            this.tokenStorage = tokenStorage;
            this.pendingLoginStorage = pendingLoginStorage;
        }
    }

    public static Deps create(Context context, String baseUrl) {
        SharedPreferences authSp = context.getSharedPreferences("auth_sp", Context.MODE_PRIVATE);
        SharedPreferences pendingSp = context.getSharedPreferences("pending_login_sp", Context.MODE_PRIVATE);

        TokenStorage tokenStorage = new TokenStorage(authSp);
        PendingLoginStorage pendingLoginStorage = new PendingLoginStorage(pendingSp);

        OkHttpClient client = new OkHttpClient.Builder().build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        AuthApi api = retrofit.create(AuthApi.class);

        return new Deps(api, tokenStorage, pendingLoginStorage);
    }
}