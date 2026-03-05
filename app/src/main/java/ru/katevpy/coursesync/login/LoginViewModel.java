package ru.katevpy.coursesync.login;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private @Nullable ScheduledFuture<?> expiryTask = null;

    public LoginViewModel(AuthRepository repo) {
        this.repo = repo;

        if (repo.hasPending()) {
            ui.setValue(new LoginUiState(Step.ENTER_CODE, false, null, null, null, false));
            scheduleExpiryTimer();
        }
    }

    public LiveData<LoginUiState> getUi() {
        return ui;
    }

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean isHseEmail(String email) {
        int at = email.lastIndexOf('@');
        if (at == -1) return false;

        String domain = email.substring(at + 1).toLowerCase();
        return domain.equals("edu.hse.ru");
    }

    private void showEmailRequired() {
        ui.postValue(new LoginUiState(
                Step.ENTER_EMAIL,
                false,
                "Поле не должно быть пустым",
                null,
                null,
                false
        ));
    }

    private void showInvalidEmail() {
        ui.postValue(new LoginUiState(
                Step.ENTER_EMAIL,
                false,
                "Введите корректную почту",
                null,
                null,
                false
        ));
    }

    private void showNotHseEmail() {
        ui.postValue(new LoginUiState(
                Step.ENTER_EMAIL,
                false,
                "Поддерживается только корпоративная почта ВШЭ",
                null,
                null,
                false
        ));
    }

    private void showCodeRequired() {
        ui.postValue(new LoginUiState(
                Step.ENTER_CODE,
                false,
                null,
                "Введите код",
                null,
                false
        ));
    }

    private void scheduleExpiryTimer() {
        if (expiryTask != null) {
            expiryTask.cancel(true);
            expiryTask = null;
        }

        if (!repo.hasPending()) return;

        long expiresAtMs = repo.getPendingExpiresAtMs();
        long now = System.currentTimeMillis();
        long delayMs = expiresAtMs - now;

        if (delayMs <= 0) {
            repo.clearPending();
            ui.postValue(new LoginUiState(
                    Step.ENTER_EMAIL,
                    false,
                    null,
                    null,
                    "Код истёк. Отправьте новый",
                    false
            ));
            return;
        }

        expiryTask = scheduler.schedule(() -> {
            LoginUiState s = ui.getValue();
            if (s != null && s.navigateToApp) return;

            repo.clearPending();
            ui.postValue(new LoginUiState(
                    Step.ENTER_EMAIL,
                    false,
                    null,
                    null,
                    "Код истёк. Отправьте новый",
                    false
            ));
        }, delayMs, TimeUnit.MILLISECONDS);
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

        if (email.isEmpty()) {
            showEmailRequired();
            return;
        }

        if (!isValidEmail(email)) {
            showInvalidEmail();
            return;
        }

        if (!isHseEmail(email)) {
            showNotHseEmail();
            return;
        }

        ui.setValue(new LoginUiState(Step.ENTER_EMAIL, true, null, null, null, false));

        io.execute(() -> {
            Result<SendCodeResponse> r = repo.startLogin(email);

            if (r instanceof Result.Success) {
                ui.postValue(new LoginUiState(Step.ENTER_CODE, false, null, null, null, false));
                scheduleExpiryTimer();
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
            showCodeRequired();
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

                if (expiryTask != null) {
                    expiryTask.cancel(true);
                    expiryTask = null;
                }

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
                showEmailRequired();
                return;
            }
            if ("invalid_email".equals(code)) {
                showInvalidEmail();
                return;
            }
            if ("not_hse_email".equals(code)) {
                showNotHseEmail();
                return;
            }
        }

        if (http == 429 && "rate_limited".equals(code)) {
            ui.postValue(new LoginUiState(Step.ENTER_EMAIL, false, null, null, "Можно отправить только 1 код в минуту", false));
            return;
        }

        if (http == 500 && "email_send_failed".equals(code)) {
            ui.postValue(new LoginUiState(Step.ENTER_EMAIL, false, null, null, "Не удалось отправить код. Попробуйте позже", false));
            return;
        }

        ui.postValue(new LoginUiState(Step.ENTER_EMAIL, false, null, null, "Внутренняя ошибка. Уже работаем над исправлением", false));
    }

    private void postLoginHttpError(int http, @Nullable ApiError apiError) {
        String code = apiError != null ? apiError.code : null;

        if (http == 400) {
            if ("email_required".equals(code)) {
                showEmailRequired();
                return;
            }
            if ("invalid_email".equals(code)) {
                showInvalidEmail();
                return;
            }
            if ("not_hse_email".equals(code)) {
                showNotHseEmail();
                return;
            }
            if ("code_required".equals(code)) {
                showCodeRequired();
                return;
            }
        }

        if (http == 401 && "invalid_code".equals(code)) {
            ui.postValue(new LoginUiState(Step.ENTER_CODE, false, null, "Неверный код", null, false));
            return;
        }

        if (http == 429 && "code_attempts_exceeded".equals(code)) {
            repo.clearPending();

            ui.postValue(new LoginUiState(
                    Step.ENTER_EMAIL,
                    false,
                    null,
                    null,
                    "Слишком много попыток",
                    false
            ));
            return;
        }

        ui.postValue(new LoginUiState(Step.ENTER_CODE, false, null, null, "Внутренняя ошибка. Уже работаем над исправлением", false));
    }

    @Override
    protected void onCleared() {
        io.shutdownNow();
        scheduler.shutdownNow();
    }
}