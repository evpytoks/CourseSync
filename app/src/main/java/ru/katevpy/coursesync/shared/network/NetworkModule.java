package ru.katevpy.coursesync.shared.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import ru.katevpy.coursesync.shared.storage.PendingLoginStorage;
import ru.katevpy.coursesync.shared.storage.TokenStorage;

public final class NetworkModule {

    public static final class Deps {
        public final AuthApi authApi;
        public final GroupApi groupApi;
        public final TokenStorage tokenStorage;
        public final PendingLoginStorage pendingLoginStorage;

        Deps(
                AuthApi authApi,
                GroupApi groupApi,
                TokenStorage tokenStorage,
                PendingLoginStorage pendingLoginStorage
        ) {
            this.authApi = authApi;
            this.groupApi = groupApi;
            this.tokenStorage = tokenStorage;
            this.pendingLoginStorage = pendingLoginStorage;
        }
    }

    public static Deps create(Context context, String baseUrl) {
        SharedPreferences authSp = context.getSharedPreferences("auth_sp", Context.MODE_PRIVATE);
        SharedPreferences pendingSp = context.getSharedPreferences("pending_login_sp", Context.MODE_PRIVATE);

        TokenStorage tokenStorage = new TokenStorage(authSp);
        PendingLoginStorage pendingLoginStorage = new PendingLoginStorage(pendingSp);

        OkHttpClient clientNoAuth = new OkHttpClient.Builder()
                .addInterceptor(new AuthInterceptor(tokenStorage))
                .build();

        Retrofit retrofitAuth = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(clientNoAuth)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        AuthApi authApi = retrofitAuth.create(AuthApi.class);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(message -> Log.d("OkHttp", message));
        logging.setLevel(HttpLoggingInterceptor.Level.HEADERS);

        OkHttpClient clientWithAuth = new OkHttpClient.Builder()
                .addInterceptor(new AuthInterceptor(tokenStorage))
                .addInterceptor(logging)
                .authenticator(new TokenAuthenticator(tokenStorage, authApi))
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(clientWithAuth)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        GroupApi groupApi = retrofit.create(GroupApi.class);

        return new Deps(authApi, groupApi, tokenStorage, pendingLoginStorage);
    }
}