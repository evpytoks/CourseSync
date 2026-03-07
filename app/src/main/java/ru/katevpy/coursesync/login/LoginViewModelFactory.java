package ru.katevpy.coursesync.login;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import ru.katevpy.coursesync.App;
import ru.katevpy.coursesync.shared.repository.AuthRepository;

public final class LoginViewModelFactory implements ViewModelProvider.Factory {

    private final android.content.Context appContext;

    public LoginViewModelFactory(android.content.Context appContext) {
        this.appContext = appContext;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        ru.katevpy.coursesync.shared.network.NetworkModule.Deps deps = App.getDeps();
        AuthRepository repo = new AuthRepository(deps.authApi, deps.pendingLoginStorage, deps.tokenStorage);
        return (T) new LoginViewModel(repo);
    }
}