package ru.katevpy.coursesync.login;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import ru.katevpy.coursesync.shared.network.NetworkModule;
import ru.katevpy.coursesync.shared.repository.AuthRepository;

public final class LoginViewModelFactory implements ViewModelProvider.Factory {

    private final Context appContext;

    public LoginViewModelFactory(Context appContext) {
        this.appContext = appContext;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        String baseUrl = "http://10.0.2.2:5065/";

        NetworkModule.Deps deps = NetworkModule.create(appContext, baseUrl);
        AuthRepository repo = new AuthRepository(deps.authApi, deps.pendingLoginStorage, deps.tokenStorage);

        return (T) new LoginViewModel(repo);
    }
}