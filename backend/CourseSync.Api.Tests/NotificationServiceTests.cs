using CourseSync.Api.Services;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class NotificationServiceTests
{
    [Fact]
    public async Task CreateNewsAndPushAsync_rejects_empty_title()
    {
        await using var tdb = new TestDb();
        var service = new NotificationService(tdb.Db);

        await Assert.ThrowsAsync<ArgumentException>(() => service.CreateNewsAndPushAsync(
            "manual",
            Guid.NewGuid(),
            Guid.NewGuid(),
            "   ",
            "",
            CancellationToken.None));
    }

    [Fact]
    public async Task CreateNewsAndPushAsync_rejects_too_long_body()
    {
        await using var tdb = new TestDb();
        var service = new NotificationService(tdb.Db);

        await Assert.ThrowsAsync<ArgumentException>(() => service.CreateNewsAndPushAsync(
            "manual",
            Guid.NewGuid(),
            Guid.NewGuid(),
            "title",
            new string('b', 3001),
            CancellationToken.None));
    }

    [Fact]
    public async Task CreateNewsAndPushAsync_accepts_valid_lengths()
    {
        await using var tdb = new TestDb();
        var service = new NotificationService(tdb.Db);

        await service.CreateNewsAndPushAsync(
            "manual",
            Guid.NewGuid(),
            Guid.NewGuid(),
            new string('t', 50),
            new string('b', 3000),
            CancellationToken.None);
    }
}
