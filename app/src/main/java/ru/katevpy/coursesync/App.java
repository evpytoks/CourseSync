package ru.katevpy.coursesync;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import ru.katevpy.coursesync.shared.network.NetworkModule;

public class App extends Application {

    private static final String BASE_URL = "http://62.217.176.79/";

    private static NetworkModule.Deps deps;

    /**
     * If user taps a push before login, open the news tab after successful sign-in.
     */
    public volatile boolean pendingOpenNewsListFromNotification;

    public static NetworkModule.Deps getDeps() {
        if (deps == null) {
            throw new IllegalStateException("App.getDeps() called before onCreate");
        }
        return deps;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        deps = NetworkModule.create(this, BASE_URL);
    }
}
