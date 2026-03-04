package ru.katevpy.coursesync.login;

import androidx.annotation.Nullable;

public final class LoginUiState {

    public final LoginViewModel.Step step;
    public final boolean loading;

    @Nullable public final String emailError;
    @Nullable public final String codeError;
    @Nullable public final String message;

    public final boolean navigateToApp;

    public LoginUiState(
            LoginViewModel.Step step,
            boolean loading,
            @Nullable String emailError,
            @Nullable String codeError,
            @Nullable String message,
            boolean navigateToApp
    ) {
        this.step = step;
        this.loading = loading;
        this.emailError = emailError;
        this.codeError = codeError;
        this.message = message;
        this.navigateToApp = navigateToApp;
    }

    public static LoginUiState initial() {
        return new LoginUiState(LoginViewModel.Step.ENTER_EMAIL, false, null, null, null, false);
    }

    public LoginUiState copy(
            @Nullable LoginViewModel.Step step,
            @Nullable Boolean loading,
            @Nullable String emailError,
            @Nullable String codeError,
            @Nullable String message,
            @Nullable Boolean navigateToApp
    ) {
        return new LoginUiState(
                step != null ? step : this.step,
                loading != null ? loading : this.loading,
                emailError,
                codeError,
                message,
                navigateToApp != null ? navigateToApp : this.navigateToApp
        );
    }
}
