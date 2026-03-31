package ru.katevpy.coursesync;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

import ru.katevpy.coursesync.shared.network.NetworkModule;

public class App extends Application {

    private static final String BASE_URL = "http://62.217.176.79/";

    private static NetworkModule.Deps deps;

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
        DynamicColors.applyToActivitiesIfAvailable(this);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        deps = NetworkModule.create(this, BASE_URL);
    }
}
