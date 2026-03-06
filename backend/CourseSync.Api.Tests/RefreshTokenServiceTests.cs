using CourseSync.Api.Data;
using CourseSync.Api.Infrastructure;
using CourseSync.Api.Services;
using Microsoft.Extensions.Options;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class RefreshTokenServiceTests
{
    private static RefreshTokenService CreateService(AppDbContext db)
    {
        return new RefreshTokenService(db, Options.Create(new AuthTokensOptions
        {
            RefreshTokenTtlDays = 30,
            RefreshTokenHashKey = "SUPER_LONG_SECRET_KEY_CHANGE_ME_REFRESHTOKEN_1234567890"
        }));
    }

    [Fact]
    public async Task RotateAsync_empty_token_returns_Invalid()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var (status, _, _, _) = await CreateService(tdb.Db).RotateAsync("", CancellationToken.None);
        Assert.Equal(RefreshRotateStatus.Invalid, status);
    }

    [Fact]
    public async Task EstablishSingleSessionAsync_returns_token_and_bumps_version()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var (refreshToken, expiresAt, tokenVersion) = await CreateService(tdb.Db).EstablishSingleSessionAsync(user.Id, CancellationToken.None);
        Assert.False(string.IsNullOrWhiteSpace(refreshToken));
        Assert.True(expiresAt > DateTimeOffset.UtcNow);
        Assert.True(tokenVersion >= 1);
    }

    [Fact]
    public async Task RevokeAsync_invalidates_token()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = CreateService(tdb.Db);
        var (plain, _, _) = await svc.EstablishSingleSessionAsync(user.Id, CancellationToken.None);
        await svc.RevokeAsync(plain, CancellationToken.None);
        var (status, _, _, _) = await svc.RotateAsync(plain, CancellationToken.None);
        Assert.Equal(RefreshRotateStatus.RevokedOrExpired, status);
    }
}
