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

public sealed class AuthControllerHappyPathTests
{
    [Fact]
    public async Task Full_flow_send_code_then_login_then_refresh_works()
    {
        await using var tdb = new TestDb();
        var (controller, emailSender) = CreateController(tdb.Db);

        var email = "user@edu.hse.ru";

        var send = await controller.SendCode(new SendCodeRequest(email), CancellationToken.None);
        var sendOk = Assert.IsType<OkObjectResult>(send.Result);
        var sendPayload = Assert.IsType<SendCodeResponse>(sendOk.Value);
        Assert.StartsWith("req_", sendPayload.RequestId);
        Assert.NotEqual(default, sendPayload.ExpiresAt);
        Assert.NotNull(emailSender.LastCode);

        var login = await controller.Login(new LoginRequest(email, sendPayload.RequestId, emailSender.LastCode!), CancellationToken.None);
        var loginOk = Assert.IsType<OkObjectResult>(login.Result);
        var loginPayload = Assert.IsType<LoginResponse>(loginOk.Value);
        Assert.False(string.IsNullOrWhiteSpace(loginPayload.Token));
        Assert.False(string.IsNullOrWhiteSpace(loginPayload.RefreshToken));

        var refresh = await controller.Refresh(new RefreshRequest(loginPayload.RefreshToken), CancellationToken.None);
        var refreshOk = Assert.IsType<OkObjectResult>(refresh.Result);
        var refreshPayload = Assert.IsType<RefreshResponse>(refreshOk.Value);
        Assert.False(string.IsNullOrWhiteSpace(refreshPayload.Token));
        Assert.False(string.IsNullOrWhiteSpace(refreshPayload.RefreshToken));
        Assert.NotEqual(loginPayload.RefreshToken, refreshPayload.RefreshToken);

        var reusedRefresh = await controller.Refresh(new RefreshRequest(loginPayload.RefreshToken), CancellationToken.None);
        var reuseUnauthorized = Assert.IsType<UnauthorizedObjectResult>(reusedRefresh.Result);
        Assert.Equal("refresh_reused", Assert.IsType<ErrorEnvelope>(reuseUnauthorized.Value).Error.Code);
    }

    private static (AuthController controller, CapturingEmailSender email) CreateController(AppDbContext db)
    {
        var cfg = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["Smtp:Enabled"] = "false"
            })
            .Build();

        var authOpt = new AuthCodeOptions
        {
            CodeTtlSeconds = 300,
            SendCooldownSeconds = 0,
            MaxAttempts = 3,
            HashKey = "SUPER_LONG_SECRET_KEY_CHANGE_ME_AUTHCODE_1234567890"
        };

        var codes = new AuthLoginCodeService(db, Options.Create(authOpt), NullLogger<AuthLoginCodeService>.Instance);
        var refresh = new RefreshTokenService(db, Options.Create(new AuthTokensOptions
        {
            RefreshTokenTtlDays = 30,
            RefreshTokenHashKey = "SUPER_LONG_SECRET_KEY_CHANGE_ME_REFRESHTOKEN_1234567890"
        }));

        var email = new CapturingEmailSender();
        var users = new UserService(db);
        var jwt = new JwtTokenService(Options.Create(new JwtOptions
        {
            Issuer = "CourseSync",
            Audience = "CourseSyncMobile",
            Key = "SUPER_LONG_SECRET_KEY_CHANGE_ME_1234567890",
            AccessTokenMinutes = 60
        }));

        return (new AuthController(
            codes,
            jwt,
            Options.Create(authOpt),
            cfg,
            email,
            NullLogger<AuthController>.Instance,
            users,
            refresh), email);
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
}
