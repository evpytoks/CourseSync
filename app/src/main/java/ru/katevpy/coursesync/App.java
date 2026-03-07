package ru.katevpy.coursesync;

import android.app.Application;

import ru.katevpy.coursesync.shared.network.NetworkModule;

public class App extends Application {

    private static final String BASE_URL = "http://10.0.2.2:5065/";

    private static NetworkModule.Deps deps;

    public static NetworkModule.Deps getDeps() {
        if (deps == null) {
            throw new IllegalStateException("App.getDeps() called before onCreate");
        }
        return deps;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        deps = NetworkModule.create(this, BASE_URL);
    }
}
