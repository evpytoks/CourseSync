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
                ["Jwt:Issuer"] = "CourseSync",
                ["Jwt:Audience"] = "CourseSyncMobile",
                ["Jwt:Key"] = "SUPER_LONG_SECRET_KEY_CHANGE_ME_1234567890",
                ["Jwt:AccessTokenMinutes"] = "60",
                ["Smtp:Enabled"] = "false"
            })
            .Build();

    private static (AuthController controller, TestEmailSender email) CreateController(AppDbContext db, AuthCodeOptions authOpt)
    {
        var cfg = CreateCfg();

        var codes = new AuthLoginCodeService(
            db,
            Options.Create(new AuthCodeOptions
            {
                CodeTtlSeconds = authOpt.CodeTtlSeconds,
                SendCooldownSeconds = authOpt.SendCooldownSeconds,
                MaxAttempts = authOpt.MaxAttempts,
                LockoutSeconds = authOpt.LockoutSeconds,
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

        var email = new TestEmailSender();
        var users = new UserService(db);
        var jwt = new JwtTokenService(cfg);

        var controller = new AuthController(
            codes,
            jwt,
            Options.Create(authOpt),
            cfg,
            email,
            NullLogger<AuthController>.Instance,
            users,
            refresh);
        return (controller, email);
    }

    [Theory]
    [InlineData("", "email_required")]
    [InlineData("   ", "email_required")]
    [InlineData("not-an-email", "invalid_email")]
    [InlineData("user@hse.ru", "email_domain_not_allowed")]
    public async Task SendCode_validates_email(string email, string expectedCode)
    {
        await using var tdb = new TestDb();
        var (controller, _) = CreateController(tdb.Db, new AuthCodeOptions { CodeTtlSeconds = 300, SendCooldownSeconds = 60, MaxAttempts = 3, LockoutSeconds = 600 });

        var res = await controller.SendCode(new SendCodeRequest(email), CancellationToken.None);

        var bad = Assert.IsType<BadRequestObjectResult>(res.Result);
        var envelope = Assert.IsType<ErrorEnvelope>(bad.Value);
        Assert.Equal(expectedCode, envelope.Error.Code);
    }

    [Fact]
    public async Task SendCode_rate_limits_second_request()
    {
        await using var tdb = new TestDb();
        var (controller, emailSender) = CreateController(tdb.Db, new AuthCodeOptions { CodeTtlSeconds = 300, SendCooldownSeconds = 60, MaxAttempts = 3, LockoutSeconds = 600 });

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
        var (controller, _) = CreateController(tdb.Db, new AuthCodeOptions { CodeTtlSeconds = 300, SendCooldownSeconds = 60, MaxAttempts = 3, LockoutSeconds = 600 });

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
    [InlineData("user@hse.ru", "email_domain_not_allowed")]
    public async Task Login_validates_email(string email, string expectedCode)
    {
        await using var tdb = new TestDb();
        var (controller, _) = CreateController(tdb.Db, new AuthCodeOptions { CodeTtlSeconds = 300, SendCooldownSeconds = 60, MaxAttempts = 3, LockoutSeconds = 600 });

        var res = await controller.Login(new LoginRequest(email, "req_x", "123456"), CancellationToken.None);
        var bad = Assert.IsType<BadRequestObjectResult>(res.Result);
        var envelope = Assert.IsType<ErrorEnvelope>(bad.Value);
        Assert.Equal(expectedCode, envelope.Error.Code);
    }

    [Fact]
    public async Task Refresh_requires_refresh_token()
    {
        await using var tdb = new TestDb();
        var (controller, _) = CreateController(tdb.Db, new AuthCodeOptions { CodeTtlSeconds = 300, SendCooldownSeconds = 60, MaxAttempts = 3, LockoutSeconds = 600 });

        var res = await controller.Refresh(new RefreshRequest(""), CancellationToken.None);
        var bad = Assert.IsType<BadRequestObjectResult>(res.Result);
        Assert.Equal("refresh_token_required", Assert.IsType<ErrorEnvelope>(bad.Value).Error.Code);
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
}
