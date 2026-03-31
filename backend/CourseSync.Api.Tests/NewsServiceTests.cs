using CourseSync.Api.Data;
using CourseSync.Api.Services;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class NewsServiceTests
{
    private static NewsService CreateSvc(TestDb tdb) =>
        new(tdb.Db, new NotificationService(tdb.Db));

    [Fact]
    public async Task GetAllForUserAsync_returns_news_from_all_groups_user_belongs_to()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        var g1 = new Group
        {
            Id = Guid.NewGuid(),
            Name = "G1",
            Code = "aaaaaa",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var g2 = new Group
        {
            Id = Guid.NewGuid(),
            Name = "G2",
            Code = "bbbbbb",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var t1 = DateTimeOffset.UtcNow.AddHours(-2);
        var t2 = DateTimeOffset.UtcNow.AddHours(-1);
        var n1 = new News
        {
            Id = Guid.NewGuid(),
            GroupId = g1.Id,
            CreatedAt = t1,
            GroupName = g1.Name,
            Section = "s1",
            Detail = "d1",
            Type = "manual"
        };
        var n2 = new News
        {
            Id = Guid.NewGuid(),
            GroupId = g2.Id,
            CreatedAt = t2,
            GroupName = g2.Name,
            Section = "s2",
            Detail = "d2",
            Type = "manual"
        };
        tdb.Db.Users.Add(user);
        tdb.Db.Groups.AddRange(g1, g2);
        tdb.Db.GroupMembers.AddRange(
            new GroupMember { GroupId = g1.Id, UserId = user.Id, Role = GroupRole.Participant, JoinedAt = DateTimeOffset.UtcNow },
            new GroupMember { GroupId = g2.Id, UserId = user.Id, Role = GroupRole.Participant, JoinedAt = DateTimeOffset.UtcNow });
        tdb.Db.News.AddRange(n1, n2);
        await tdb.Db.SaveChangesAsync();

        var svc = CreateSvc(tdb);
        var list = await svc.GetAllForUserAsync(user.Id, CancellationToken.None);

        Assert.Equal(2, list.Count);
        Assert.Equal(n2.Id, list[0].Id);
        Assert.Equal(n1.Id, list[1].Id);
    }
}
