package ru.katevpy.coursesync.shared.repository;

import com.google.gson.Gson;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.ResponseBody;
import retrofit2.Response;

import ru.katevpy.coursesync.shared.dto.*;
import ru.katevpy.coursesync.shared.network.AuthApi;
import ru.katevpy.coursesync.shared.storage.PendingLoginStorage;
import ru.katevpy.coursesync.shared.storage.TokenStorage;
import ru.katevpy.coursesync.shared.util.Result;

public final class AuthRepository {

    private final AuthApi api;
    private final PendingLoginStorage pending;
    private final TokenStorage tokens;
    private final Gson gson = new Gson();

    public AuthRepository(AuthApi api, PendingLoginStorage pending, TokenStorage tokens) {
        this.api = api;
        this.pending = pending;
        this.tokens = tokens;
    }

    public Result<SendCodeResponse> startLogin(String email) {
        try {
            Response<SendCodeResponse> r = api.sendCode(new SendCodeRequest(email)).execute();

            if (r.isSuccessful() && r.body() != null) {
                SendCodeResponse body = r.body();

                if (body.requestId == null || body.requestId.trim().isEmpty()) {
                    return Result.logicalError("Сервер не вернул requestId");
                }
                if (body.expiresAt == null || body.expiresAt.trim().isEmpty()) {
                    return Result.logicalError("Сервер не вернул expiresAt");
                }

                long expiresAtMs = parseExpiresAtMs(body.expiresAt);
                if (expiresAtMs <= 0) {
                    expiresAtMs = System.currentTimeMillis() + 5 * 60_000L;
                }

                pending.save(email, body.requestId, expiresAtMs);
                return Result.success(body);
            }

            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    public Result<LoginResponse> verifyCode(String code) {
        if (!pending.hasPending()) {
            if (pending.isExpired()) {
                pending.clear();
                return Result.logicalError("Код истёк. Запросите новый");
            }
            return Result.logicalError("Сначала введите почту");
        }

        String email = pending.getEmail();
        String requestId = pending.getRequestId();

        try {
            Response<LoginResponse> r = api.login(new LoginRequest(email, requestId, code)).execute();

            if (r.isSuccessful() && r.body() != null) {
                LoginResponse body = r.body();
                tokens.save(body.token, body.refreshToken);
                pending.clear();
                return Result.success(body);
            }

            return Result.httpError(r.code(), parseError(r.errorBody()));
        } catch (IOException e) {
            return Result.networkError(e);
        }
    }

    private ApiError parseError(ResponseBody body) {
        if (body == null) return null;
        try {
            ErrorEnvelope env = gson.fromJson(body.string(), ErrorEnvelope.class);
            return env != null ? env.error : null;
        } catch (Exception e) {
            return null;
        }
    }

    private long parseExpiresAtMs(String iso) {
        String s = iso.trim();

        long ms = tryParseIso(s, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        if (ms > 0) return ms;

        ms = tryParseIso(s, "yyyy-MM-dd'T'HH:mm:ss'Z'");
        if (ms > 0) return ms;

        return -1;
    }

    private long tryParseIso(String s, String pattern) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(s).getTime();
        } catch (ParseException e) {
            return -1;
        }
    }
}