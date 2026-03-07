using CourseSync.Api.Controllers;
using CourseSync.Api.Data;
using CourseSync.Api.Infrastructure;
using CourseSync.Api.Infrastructure.Email;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Options;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class AuthControllerValidationTests
{
    private static IConfiguration CreateCfg() =>
        new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["Smtp:Enabled"] = "false"
            })
            .Build();

    private static JwtOptions CreateJwtOptions() => new()
    {
        Issuer = "CourseSync",
        Audience = "CourseSyncMobile",
        Key = "SUPER_LONG_SECRET_KEY_CHANGE_ME_1234567890",
        AccessTokenMinutes = 60
    };

    private static (AuthController controller, TestEmailSender email) CreateController(AppDbContext db, AuthCodeOptions authOpt)
    {
        var email = new TestEmailSender();
        var (ctrl, _) = CreateControllerWithSender(db, authOpt, email);
        return (ctrl, email);
    }

    private static (AuthController controller, IEmailSender email) CreateControllerWithSender(AppDbContext db, AuthCodeOptions authOpt, IEmailSender emailSender)
    {
        var cfg = CreateCfg();

        var codes = new AuthLoginCodeService(
            db,
            Options.Create(new AuthCodeOptions
            {
                CodeTtlSeconds = authOpt.CodeTtlSeconds,
                SendCooldownSeconds = authOpt.SendCooldownSeconds,
                MaxAttempts = authOpt.MaxAttempts,
                HashKey = "SUPER_LONG_SECRET_KEY_CHANGE_ME_AUTHCODE_1234567890"
            }),
            NullLogger<AuthLoginCodeService>.Instance);

        var refresh = new RefreshTokenService(
            db,
            Options.Create(new AuthTokensOptions
            {
                RefreshTokenTtlDays = 30,
                RefreshTokenHashKey = "SUPER_LONG_SECRET_KEY_CHANGE_ME_REFRESHTOKEN_1234567890"
            }));

        var users = new UserService(db);
        var jwt = new JwtTokenService(Options.Create(CreateJwtOptions()));

        var controller = new AuthController(
            codes,
            jwt,
            Options.Create(authOpt),
            cfg,
            emailSender,
            NullLogger<AuthController>.Instance,
            users,
            refresh);
        return (controller, emailSender);
    }

    [Theory]
    [InlineData("", "email_required")]
    [InlineData("   ", "email_required")]
    [InlineData("not-an-email", "invalid_email")]
    [InlineData("user@hse.ru", "not_hse_email")]
    public async Task SendCode_validates_email(string email, string expectedCode)
    {
        await using var tdb = new TestDb();
        var (controller, _) = CreateController(tdb.Db, new AuthCodeOptions { CodeTtlSeconds = 300, SendCooldownSeconds = 60, MaxAttempts = 3 });

        var res = await controller.SendCode(new SendCodeRequest(email), CancellationToken.None);

        var bad = Assert.IsType<BadRequestObjectResult>(res.Result);
        var envelope = Assert.IsType<ErrorEnvelope>(bad.Value);
        Assert.Equal(expectedCode, envelope.Error.Code);
    }

    [Fact]
    public async Task SendCode_rate_limits_second_request()
    {
        await using var tdb = new TestDb();
        var (controller, emailSender) = CreateController(tdb.Db, new AuthCodeOptions { CodeTtlSeconds = 300, SendCooldownSeconds = 60, MaxAttempts = 3 });

        var email = "user@edu.hse.ru";

        var before = DateTimeOffset.UtcNow;
        var first = await controller.SendCode(new SendCodeRequest(email), CancellationToken.None);
        var ok = Assert.IsType<OkObjectResult>(first.Result);
        var payload = Assert.IsType<SendCodeResponse>(ok.Value);
        Assert.StartsWith("req_", payload.RequestId);
        Assert.InRange(payload.ExpiresAt, before.AddSeconds(295), DateTimeOffset.UtcNow.AddSeconds(305));
        Assert.Equal(1, emailSender.CallCount);

        var second = await controller.SendCode(new SendCodeRequest(email), CancellationToken.None);
        var limited = Assert.IsType<ObjectResult>(second.Result);
        Assert.Equal(429, limited.StatusCode);

        var envelope = Assert.IsType<ErrorEnvelope>(limited.Value);
        Assert.Equal("rate_limited", envelope.Error.Code);
        Assert.Equal(1, emailSender.CallCount);
    }

    [Fact]
    public async Task Login_requires_request_id_and_code()
    {
        await using var tdb = new TestDb();
        var (controller, _) = CreateController(tdb.Db, new AuthCodeOptions { CodeTtlSeconds = 300, SendCooldownSeconds = 60, MaxAttempts = 3 });

        var missingRequestId = await controller.Login(new LoginRequest("user@edu.hse.ru", "", "123456"), CancellationToken.None);
        var br1 = Assert.IsType<BadRequestObjectResult>(missingRequestId.Result);
        Assert.Equal("request_id_required", Assert.IsType<ErrorEnvelope>(br1.Value).Error.Code);

        var missingCode = await controller.Login(new LoginRequest("user@edu.hse.ru", "req_x", ""), CancellationToken.None);
        var br2 = Assert.IsType<BadRequestObjectResult>(missingCode.Result);
        Assert.Equal("code_required", Assert.IsType<ErrorEnvelope>(br2.Value).Error.Code);
    }

    [Theory]
    [InlineData("", "email_required")]
    [InlineData("   ", "email_required")]
    [InlineData("not-an-email", "invalid_email")]
    [InlineData("user@hse.ru", "not_hse_email")]
    public async Task Login_validates_email(string email, string expectedCode)
    {
        await using var tdb = new TestDb();
        var (controller, _) = CreateController(tdb.Db, new AuthCodeOptions { CodeTtlSeconds = 300, SendCooldownSeconds = 60, MaxAttempts = 3 });

        var res = await controller.Login(new LoginRequest(email, "req_x", "123456"), CancellationToken.None);
        var bad = Assert.IsType<BadRequestObjectResult>(res.Result);
        var envelope = Assert.IsType<ErrorEnvelope>(bad.Value);
        Assert.Equal(expectedCode, envelope.Error.Code);
    }

    [Fact]
    public async Task Refresh_requires_refresh_token()
    {
        await using var tdb = new TestDb();
        var (controller, _) = CreateController(tdb.Db, new AuthCodeOptions { CodeTtlSeconds = 300, SendCooldownSeconds = 60, MaxAttempts = 3 });

        var res = await controller.Refresh(new RefreshRequest(""), CancellationToken.None);
        var bad = Assert.IsType<BadRequestObjectResult>(res.Result);
        Assert.Equal("refresh_token_required", Assert.IsType<ErrorEnvelope>(bad.Value).Error.Code);
    }

    [Fact]
    public async Task Login_wrong_code_returns_401_invalid_code()
    {
        await using var tdb = new TestDb();
        var (controller, _) = CreateController(tdb.Db, new AuthCodeOptions { CodeTtlSeconds = 300, SendCooldownSeconds = 0, MaxAttempts = 3 });

        var email = "user@edu.hse.ru";
        var send = await controller.SendCode(new SendCodeRequest(email), CancellationToken.None);
        var sendOk = Assert.IsType<OkObjectResult>(send.Result);
        var sendPayload = Assert.IsType<SendCodeResponse>(sendOk.Value);

        var login = await controller.Login(new LoginRequest(email, sendPayload.RequestId, "000000"), CancellationToken.None);
        var unauth = Assert.IsType<UnauthorizedObjectResult>(login.Result);
        Assert.Equal("invalid_code", Assert.IsType<ErrorEnvelope>(unauth.Value).Error.Code);
    }

    [Fact]
    public async Task Login_three_wrong_attempts_returns_429_code_attempts_exceeded()
    {
        await using var tdb = new TestDb();
        var (controller, _) = CreateController(tdb.Db, new AuthCodeOptions { CodeTtlSeconds = 300, SendCooldownSeconds = 0, MaxAttempts = 3 });

        var email = "user@edu.hse.ru";
        var send = await controller.SendCode(new SendCodeRequest(email), CancellationToken.None);
        var sendOk = Assert.IsType<OkObjectResult>(send.Result);
        var sendPayload = Assert.IsType<SendCodeResponse>(sendOk.Value);

        for (int i = 0; i < 2; i++)
        {
            var attempt = await controller.Login(new LoginRequest(email, sendPayload.RequestId, "000000"), CancellationToken.None);
            Assert.IsType<UnauthorizedObjectResult>(attempt.Result);
        }

        var third = await controller.Login(new LoginRequest(email, sendPayload.RequestId, "000000"), CancellationToken.None);
        var tooMany = Assert.IsType<ObjectResult>(third.Result);
        Assert.Equal(429, tooMany.StatusCode);
        Assert.Equal("code_attempts_exceeded", Assert.IsType<ErrorEnvelope>(tooMany.Value).Error.Code);
    }

    [Fact]
    public async Task Login_nonexistent_request_id_returns_401_invalid_code()
    {
        await using var tdb = new TestDb();
        var (controller, _) = CreateController(tdb.Db, new AuthCodeOptions { CodeTtlSeconds = 300, SendCooldownSeconds = 60, MaxAttempts = 3 });

        var login = await controller.Login(new LoginRequest("user@edu.hse.ru", "req_nonexistent", "123456"), CancellationToken.None);
        var unauth = Assert.IsType<UnauthorizedObjectResult>(login.Result);
        Assert.Equal("invalid_code", Assert.IsType<ErrorEnvelope>(unauth.Value).Error.Code);
    }

    [Fact]
    public async Task Refresh_invalid_token_returns_401_invalid_refresh_token()
    {
        await using var tdb = new TestDb();
        var (controller, _) = CreateController(tdb.Db, new AuthCodeOptions { CodeTtlSeconds = 300, SendCooldownSeconds = 60, MaxAttempts = 3 });

        var res = await controller.Refresh(new RefreshRequest("not-a-valid-refresh-token"), CancellationToken.None);
        var unauth = Assert.IsType<UnauthorizedObjectResult>(res.Result);
        Assert.Equal("invalid_refresh_token", Assert.IsType<ErrorEnvelope>(unauth.Value).Error.Code);
    }

    [Fact]
    public async Task Logout_empty_token_returns_400_refresh_token_required()
    {
        await using var tdb = new TestDb();
        var (controller, _) = CreateController(tdb.Db, new AuthCodeOptions { CodeTtlSeconds = 300, SendCooldownSeconds = 60, MaxAttempts = 3 });

        var res = await controller.Logout(new RefreshRequest("   "), CancellationToken.None);
        var bad = Assert.IsType<BadRequestObjectResult>(res);
        Assert.Equal("refresh_token_required", Assert.IsType<ErrorEnvelope>(bad.Value).Error.Code);
    }

    [Fact]
    public async Task Logout_success_returns_204()
    {
        await using var tdb = new TestDb();
        var capturingSender = new CapturingEmailSender();
        var (controller, _) = CreateControllerWithSender(tdb.Db, new AuthCodeOptions { CodeTtlSeconds = 300, SendCooldownSeconds = 0, MaxAttempts = 3 }, capturingSender);

        var email = "user@edu.hse.ru";
        var send = await controller.SendCode(new SendCodeRequest(email), CancellationToken.None);
        var sendOk = Assert.IsType<OkObjectResult>(send.Result);
        var sendPayload = Assert.IsType<SendCodeResponse>(sendOk.Value);
        var login = await controller.Login(new LoginRequest(email, sendPayload.RequestId, capturingSender.LastCode!), CancellationToken.None);
        var loginOk = Assert.IsType<OkObjectResult>(login.Result);
        var loginPayload = Assert.IsType<LoginResponse>(loginOk.Value);

        var logout = await controller.Logout(new RefreshRequest(loginPayload.RefreshToken), CancellationToken.None);
        Assert.IsType<NoContentResult>(logout);
    }

    private sealed class CapturingEmailSender : IEmailSender
    {
        public string? LastCode { get; private set; }
        public Task SendAuthCodeAsync(string toEmail, string code, int ttlSeconds, CancellationToken ct = default)
        {
            LastCode = code;
            return Task.CompletedTask;
        }
    }

    [Fact]
    public async Task SendCode_email_send_fails_returns_500_email_send_failed()
    {
        await using var tdb = new TestDb();
        var (controller, _) = CreateControllerWithSender(tdb.Db, new AuthCodeOptions { CodeTtlSeconds = 300, SendCooldownSeconds = 0, MaxAttempts = 3 }, new ThrowingEmailSender());

        var send = await controller.SendCode(new SendCodeRequest("user@edu.hse.ru"), CancellationToken.None);
        var fail = Assert.IsType<ObjectResult>(send.Result);
        Assert.Equal(500, fail.StatusCode);
        Assert.Equal("email_send_failed", Assert.IsType<ErrorEnvelope>(fail.Value).Error.Code);
    }

    private sealed class TestEmailSender : IEmailSender
    {
        public int CallCount { get; private set; }

        public Task SendAuthCodeAsync(string toEmail, string code, int ttlSeconds, CancellationToken ct = default)
        {
            CallCount++;
            return Task.CompletedTask;
        }
    }

    private sealed class ThrowingEmailSender : IEmailSender
    {
        public Task SendAuthCodeAsync(string toEmail, string code, int ttlSeconds, CancellationToken ct = default) =>
            throw new InvalidOperationException("SMTP send failed.");
    }
}
