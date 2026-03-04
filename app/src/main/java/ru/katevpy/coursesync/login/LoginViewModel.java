package ru.katevpy.coursesync.login;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import ru.katevpy.coursesync.shared.dto.ApiError;
import ru.katevpy.coursesync.shared.dto.LoginResponse;
import ru.katevpy.coursesync.shared.dto.SendCodeResponse;
import ru.katevpy.coursesync.shared.repository.AuthRepository;
import ru.katevpy.coursesync.shared.util.Result;

public class LoginViewModel extends ViewModel {

    public enum Step { ENTER_EMAIL, ENTER_CODE, VERIFY }

    private final AuthRepository repo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<LoginUiState> ui = new MutableLiveData<>(LoginUiState.initial());
    private final AtomicInteger opCounter = new AtomicInteger(0);
    private volatile boolean inFlight = false;

    public LoginViewModel(AuthRepository repo) {
        this.repo = repo;
    }

    public LiveData<LoginUiState> getUi() {
        return ui;
    }

    public void onMainButtonClicked(String emailInput, String codeInput) {
        LoginUiState s = ui.getValue();
        if (s == null) s = LoginUiState.initial();

        if (s.step == Step.ENTER_EMAIL) {
            doSendCode(emailInput);
        } else {
            doVerify(codeInput);
        }
    }

    private void doSendCode(String emailInput) {
        final String email = (emailInput == null ? "" : emailInput.trim());

        ui.setValue(new LoginUiState(Step.ENTER_EMAIL, true, null, null, null, false));

        io.execute(() -> {
            Result<SendCodeResponse> r = repo.startLogin(email);

            if (r instanceof Result.Success) {
                ui.postValue(new LoginUiState(Step.ENTER_CODE, false, null, null, null, false));
                return;
            }

            if (r instanceof Result.HttpError) {
                Result.HttpError<SendCodeResponse> e = (Result.HttpError<SendCodeResponse>) r;
                postSendCodeHttpError(e.httpCode, e.error);
                return;
            }

            if (r instanceof Result.LogicalError) {
                ui.postValue(new LoginUiState(Step.ENTER_EMAIL, false, null, null,
                        ((Result.LogicalError<SendCodeResponse>) r).message, false));
                return;
            }

            ui.postValue(new LoginUiState(Step.ENTER_EMAIL, false, null, null, "Проблема с сетью", false));
        });
    }

    private void doVerify(String codeInput) {
        final String code = (codeInput == null ? "" : codeInput.trim());

        if (code.isEmpty()) {
            ui.setValue(new LoginUiState(Step.ENTER_CODE, false, null, "Введите код", null, false));
            return;
        }

        if (inFlight) return;
        inFlight = true;

        final int myOp = opCounter.incrementAndGet();

        ui.setValue(new LoginUiState(Step.ENTER_CODE, true, null, null, null, false));

        io.execute(() -> {
            Result<LoginResponse> r = repo.verifyCode(code);

            if (myOp != opCounter.get()) {
                inFlight = false;
                return;
            }

            if (r instanceof Result.Success) {
                inFlight = false;
                ui.postValue(new LoginUiState(Step.VERIFY, false, null, null, null, true));
                return;
            }

            if (r instanceof Result.HttpError) {
                inFlight = false;
                Result.HttpError<LoginResponse> e = (Result.HttpError<LoginResponse>) r;
                postLoginHttpError(e.httpCode, e.error);
                return;
            }

            if (r instanceof Result.LogicalError) {
                inFlight = false;
                String msg = ((Result.LogicalError<LoginResponse>) r).message;
                if (msg.toLowerCase().contains("почт") || msg.toLowerCase().contains("истёк")) {
                    ui.postValue(new LoginUiState(Step.ENTER_EMAIL, false, null, null, msg, false));
                } else {
                    ui.postValue(new LoginUiState(Step.ENTER_CODE, false, null, null, msg, false));
                }
                return;
            }

            inFlight = false;
            ui.postValue(new LoginUiState(Step.ENTER_CODE, false, null, null, "Проблема с сетью", false));
        });
    }

    private void postSendCodeHttpError(int http, @Nullable ApiError apiError) {
        String code = apiError != null ? apiError.code : null;

        if (http == 400) {
            if ("email_required".equals(code)) {
                ui.postValue(new LoginUiState(Step.ENTER_EMAIL, false, "Введите почту", null, null, false));
                return;
            }
            if ("invalid_email".equals(code)) {
                ui.postValue(new LoginUiState(Step.ENTER_EMAIL, false, "Некорректная почта", null, null, false));
                return;
            }
            if ("not_hse_email".equals(code)) {
                ui.postValue(new LoginUiState(Step.ENTER_EMAIL, false, "Нужна почта edu.hse.ru", null, null, false));
                return;
            }
        }

        if (http == 429 && "rate_limited".equals(code)) {
            ui.postValue(new LoginUiState(Step.ENTER_EMAIL, false, null, null, "Слишком часто. Попробуйте позже", false));
            return;
        }

        if (http == 500 && "email_send_failed".equals(code)) {
            ui.postValue(new LoginUiState(Step.ENTER_EMAIL, false, null, null, "Не удалось отправить код. Попробуйте позже", false));
            return;
        }

        ui.postValue(new LoginUiState(Step.ENTER_EMAIL, false, null, null, "Ошибка (" + http + ")", false));
    }

    private void postLoginHttpError(int http, @Nullable ApiError apiError) {
        String code = apiError != null ? apiError.code : null;

        if (http == 400) {
            if ("email_required".equals(code)) {
                ui.postValue(new LoginUiState(Step.ENTER_EMAIL, false, "Введите почту", null, null, false));
                return;
            }
            if ("invalid_email".equals(code)) {
                ui.postValue(new LoginUiState(Step.ENTER_EMAIL, false, "Некорректная почта", null, null, false));
                return;
            }
            if ("not_hse_email".equals(code)) {
                ui.postValue(new LoginUiState(Step.ENTER_EMAIL, false, "Нужна почта edu.hse.ru", null, null, false));
                return;
            }
            if ("request_id_required".equals(code)) {
                ui.postValue(new LoginUiState(Step.ENTER_EMAIL, false, null, null, "Запросите код ещё раз", false));
                return;
            }
            if ("code_required".equals(code)) {
                ui.postValue(new LoginUiState(Step.ENTER_CODE, false, null, "Введите код", null, false));
                return;
            }
        }

        if (http == 401 && "invalid_code".equals(code)) {
            ui.postValue(new LoginUiState(Step.ENTER_CODE, false, null, "Неверный или просроченный код", null, false));
            return;
        }

        if (http == 429 && "code_attempts_exceeded".equals(code)) {
            ui.postValue(new LoginUiState(Step.ENTER_CODE, false, null, null, "Слишком много попыток. Попробуйте позже", false));
            return;
        }

        ui.postValue(new LoginUiState(Step.ENTER_CODE, false, null, null, "Ошибка (" + http + ")", false));
    }

    @Override
    protected void onCleared() {
        io.shutdownNow();
    }
}