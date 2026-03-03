using CourseSync.Api.Data;
using CourseSync.Api.Infrastructure;
using CourseSync.Api.Services;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Options;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class AuthLoginCodeServiceTests
{
    private static AuthLoginCodeService CreateService(AppDbContext db)
    {
        var opt = Options.Create(new AuthCodeOptions
        {
            CodeTtlSeconds = 300,
            SendCooldownSeconds = 60,
            MaxAttempts = 3,
            LockoutSeconds = 600,
            HashKey = "SUPER_LONG_SECRET_KEY_CHANGE_ME_AUTHCODE_1234567890"
        });

        return new AuthLoginCodeService(db, opt, NullLogger<AuthLoginCodeService>.Instance);
    }

    [Fact]
    public async Task CreateAsync_rate_limits_second_send_within_cooldown()
    {
        await using var tdb = new TestDb();
        var db = tdb.Db;

        var user = new User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        db.Users.Add(user);
        await db.SaveChangesAsync();

        var svc = CreateService(db);

        var first = await svc.CreateAsync(user, ttlSeconds: 300, cooldownSeconds: 60, CancellationToken.None);
        Assert.Equal(CreateAuthCodeStatus.Ok, first.status);

        var second = await svc.CreateAsync(user, ttlSeconds: 300, cooldownSeconds: 60, CancellationToken.None);
        Assert.Equal(CreateAuthCodeStatus.RateLimited, second.status);
    }

    [Fact]
    public async Task VerifyAsync_invalidates_request_after_max_attempts()
    {
        await using var tdb = new TestDb();
        var db = tdb.Db;

        var user = new User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        db.Users.Add(user);
        await db.SaveChangesAsync();

        var svc = CreateService(db);

        var (status, requestId, _, correctCode) = await svc.CreateAsync(user, ttlSeconds: 300, cooldownSeconds: 0, CancellationToken.None);
        Assert.Equal(CreateAuthCodeStatus.Ok, status);

        var r1 = await svc.VerifyAsync(user.Email, requestId, "000000", maxAttempts: 3, CancellationToken.None);
        Assert.Equal(VerifyResult.Invalid, r1);

        var r2 = await svc.VerifyAsync(user.Email, requestId, "000000", maxAttempts: 3, CancellationToken.None);
        Assert.Equal(VerifyResult.Invalid, r2);

        var r3 = await svc.VerifyAsync(user.Email, requestId, "000000", maxAttempts: 3, CancellationToken.None);
        Assert.Equal(VerifyResult.TooManyAttempts, r3);

        var stillThere = await db.AuthLoginRequests.SingleOrDefaultAsync(x => x.RequestId == requestId);
        Assert.Null(stillThere);

        var afterReset = await svc.VerifyAsync(user.Email, requestId, correctCode, maxAttempts: 3, CancellationToken.None);
        Assert.Equal(VerifyResult.Invalid, afterReset);
    }

    [Fact]
    public async Task CreateAsync_sets_expires_at_to_ttl()
    {
        await using var tdb = new TestDb();
        var db = tdb.Db;

        var user = new User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        db.Users.Add(user);
        await db.SaveChangesAsync();

        var svc = CreateService(db);

        var ttl = 300;
        var (status, requestId, _, _) = await svc.CreateAsync(user, ttlSeconds: ttl, cooldownSeconds: 0, CancellationToken.None);
        Assert.Equal(CreateAuthCodeStatus.Ok, status);

        var rec = await db.AuthLoginRequests.SingleAsync(x => x.RequestId == requestId);
        var sec = (rec.ExpiresAt - rec.CreatedAt).TotalSeconds;
        Assert.InRange(sec, ttl - 1, ttl + 1);
    }
}
