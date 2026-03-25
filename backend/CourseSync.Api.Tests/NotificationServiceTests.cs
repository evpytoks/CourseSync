using CourseSync.Api.Data;
using CourseSync.Api.Services;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class NotificationServiceTests
{
    private static async Task<(Guid GroupId, Guid OwnerId)> SeedGroupWithOwnerAsync(TestDb tdb)
    {
        var groupId = Guid.NewGuid();
        var ownerId = Guid.NewGuid();
        tdb.Db.Groups.Add(new Group
        {
            Id = groupId,
            Name = "MyGroup",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        });
        tdb.Db.Users.Add(new User { Id = ownerId, Email = "o@test.ru" });
        tdb.Db.GroupMembers.Add(new GroupMember
        {
            GroupId = groupId,
            UserId = ownerId,
            Role = GroupRole.Owner,
            JoinedAt = DateTimeOffset.UtcNow
        });
        await tdb.Db.SaveChangesAsync();
        return (groupId, ownerId);
    }

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
        var (groupId, ownerId) = await SeedGroupWithOwnerAsync(tdb);
        var service = new NotificationService(tdb.Db);

        await Assert.ThrowsAsync<ArgumentException>(() => service.CreateNewsAndPushAsync(
            "manual",
            ownerId,
            groupId,
            "title",
            new string('b', 3001),
            CancellationToken.None));
    }

    [Fact]
    public async Task CreateNewsAndPushAsync_accepts_valid_lengths()
    {
        await using var tdb = new TestDb();
        var (groupId, ownerId) = await SeedGroupWithOwnerAsync(tdb);
        var service = new NotificationService(tdb.Db);

        await service.CreateNewsAndPushAsync(
            "manual",
            ownerId,
            groupId,
            new string('t', 50),
            new string('b', 3000),
            CancellationToken.None);
    }

    [Fact]
    public async Task CreateNewsAndPushAsync_title_is_group_name_body_is_news_headline()
    {
        await using var tdb = new TestDb();
        var groupId = Guid.NewGuid();
        var ownerId = Guid.NewGuid();
        var participantId = Guid.NewGuid();
        tdb.Db.Groups.Add(new Group
        {
            Id = groupId,
            Name = "Algebra",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        });
        tdb.Db.Users.AddRange(
            new User { Id = ownerId, Email = "o@test.ru" },
            new User { Id = participantId, Email = "p@test.ru" });
        tdb.Db.GroupMembers.AddRange(
            new GroupMember
            {
                GroupId = groupId,
                UserId = ownerId,
                Role = GroupRole.Owner,
                JoinedAt = DateTimeOffset.UtcNow
            },
            new GroupMember
            {
                GroupId = groupId,
                UserId = participantId,
                Role = GroupRole.Participant,
                JoinedAt = DateTimeOffset.UtcNow
            });
        await tdb.Db.SaveChangesAsync();

        var service = new NotificationService(tdb.Db);
        await service.CreateNewsAndPushAsync(
            "news",
            ownerId,
            groupId,
            "Заголовок",
            "Текст новости",
            CancellationToken.None);

        var n = await tdb.Db.Notifications.AsNoTracking().SingleAsync(x => x.UserId == participantId);
        Assert.Equal("Algebra", n.Title);
        Assert.Equal("Заголовок", n.Body);

        var news = await tdb.Db.News.AsNoTracking().SingleAsync(x => x.GroupId == groupId);
        Assert.Equal("Заголовок", news.Title);
        Assert.Equal("Текст новости", news.Description);
    }
}
