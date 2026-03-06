using CourseSync.Api.Data;
using CourseSync.Api.Services;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class UserServiceTests
{
    [Fact]
    public async Task FindByEmailAsync_returns_null_when_not_found()
    {
        await using var tdb = new TestDb();
        var result = await new UserService(tdb.Db).FindByEmailAsync("nonexistent@edu.hse.ru", CancellationToken.None);
        Assert.Null(result);
    }

    [Fact]
    public async Task FindByEmailAsync_returns_user_when_found()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var result = await new UserService(tdb.Db).FindByEmailAsync("user@edu.hse.ru", CancellationToken.None);
        Assert.NotNull(result);
        Assert.Equal(user.Id, result.Id);
    }

    [Fact]
    public async Task FindByEmailAsync_normalizes_email_to_lowercase()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var result = await new UserService(tdb.Db).FindByEmailAsync("USER@EDU.HSE.RU", CancellationToken.None);
        Assert.NotNull(result);
        Assert.Equal(user.Id, result.Id);
    }

    [Fact]
    public async Task FindByIdAsync_returns_null_when_not_found()
    {
        await using var tdb = new TestDb();
        var result = await new UserService(tdb.Db).FindByIdAsync(Guid.NewGuid(), CancellationToken.None);
        Assert.Null(result);
    }

    [Fact]
    public async Task FindByIdAsync_returns_user_when_found()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var result = await new UserService(tdb.Db).FindByIdAsync(user.Id, CancellationToken.None);
        Assert.NotNull(result);
        Assert.Equal(user.Id, result.Id);
    }

    [Fact]
    public async Task ClearCurrentGroupAsync_sets_current_group_to_null()
    {
        await using var tdb = new TestDb();
        var group = new Group { Id = Guid.NewGuid(), Name = "G", Code = "ABC123", CodeGeneratedAt = DateTimeOffset.UtcNow, CreatedAt = DateTimeOffset.UtcNow };
        tdb.Db.Groups.Add(group);
        var user = new User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru", CurrentGroupId = group.Id };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        await new UserService(tdb.Db).ClearCurrentGroupAsync(user.Id, CancellationToken.None);
        var u = await tdb.Db.Users.SingleAsync(x => x.Id == user.Id);
        Assert.Null(u.CurrentGroupId);
    }

    [Fact]
    public async Task GetOrCreateByEmailAsync_creates_new_user()
    {
        await using var tdb = new TestDb();
        var result = await new UserService(tdb.Db).GetOrCreateByEmailAsync("newuser@edu.hse.ru", CancellationToken.None);
        Assert.NotNull(result);
        Assert.Equal("newuser@edu.hse.ru", result.Email);
        Assert.NotEqual(Guid.Empty, result.Id);
    }

    [Fact]
    public async Task GetOrCreateByEmailAsync_returns_existing_user()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "existing@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var result = await new UserService(tdb.Db).GetOrCreateByEmailAsync("existing@edu.hse.ru", CancellationToken.None);
        Assert.NotNull(result);
        Assert.Equal(user.Id, result.Id);
    }

    [Fact]
    public async Task GetOrCreateByEmailAsync_normalizes_email()
    {
        await using var tdb = new TestDb();
        var result = await new UserService(tdb.Db).GetOrCreateByEmailAsync("  MixedCase@Edu.HSE.Ru  ", CancellationToken.None);
        Assert.NotNull(result);
        Assert.Equal("mixedcase@edu.hse.ru", result.Email);
    }
}
