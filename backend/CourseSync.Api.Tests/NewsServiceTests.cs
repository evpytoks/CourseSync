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
        var user = new User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
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
            Section = "секция1",
            Detail = "деталь1",
            Type = "manual"
        };
        var n2 = new News
        {
            Id = Guid.NewGuid(),
            GroupId = g2.Id,
            CreatedAt = t2,
            GroupName = g2.Name,
            Section = "секция2",
            Detail = "деталь2",
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
        Assert.False(list[0].IsRead);
        Assert.False(list[1].IsRead);
    }

    [Fact]
    public async Task GetByIdAsync_marks_news_read()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        var g = new Group
        {
            Id = Guid.NewGuid(),
            Name = "Учебная группа 2026",
            Code = "aaaaaa",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var n = new News
        {
            Id = Guid.NewGuid(),
            GroupId = g.Id,
            CreatedAt = DateTimeOffset.UtcNow,
            GroupName = g.Name,
            Section = "Новости",
            Detail = "подробности новости",
            Type = "manual"
        };
        tdb.Db.Users.Add(user);
        tdb.Db.Groups.Add(g);
        tdb.Db.GroupMembers.Add(new GroupMember
        {
            GroupId = g.Id,
            UserId = user.Id,
            Role = GroupRole.Participant,
            JoinedAt = DateTimeOffset.UtcNow
        });
        tdb.Db.News.Add(n);
        await tdb.Db.SaveChangesAsync();

        var svc = CreateSvc(tdb);
        Assert.False((await svc.GetAllForUserAsync(user.Id, CancellationToken.None)).Single().IsRead);

        var (ok, _, err) = await svc.GetByIdAsync(user.Id, n.Id, CancellationToken.None);
        Assert.True(ok);
        Assert.Null(err);

        Assert.True((await svc.GetAllForUserAsync(user.Id, CancellationToken.None)).Single().IsRead);
    }

    [Fact]
    public async Task MarkAllReadAsync_marks_all_unread_visible_news()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        var g = new Group
        {
            Id = Guid.NewGuid(),
            Name = "Учебная группа 2026",
            Code = "aaaaaa",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var n1 = new News
        {
            Id = Guid.NewGuid(),
            GroupId = g.Id,
            CreatedAt = DateTimeOffset.UtcNow.AddHours(-1),
            GroupName = g.Name,
            Section = "Курсы",
            Detail = "деталь1",
            Type = "manual"
        };
        var n2 = new News
        {
            Id = Guid.NewGuid(),
            GroupId = g.Id,
            CreatedAt = DateTimeOffset.UtcNow,
            GroupName = g.Name,
            Section = "Календарь",
            Detail = "деталь2",
            Type = "manual"
        };
        tdb.Db.Users.Add(user);
        tdb.Db.Groups.Add(g);
        tdb.Db.GroupMembers.Add(new GroupMember
        {
            GroupId = g.Id,
            UserId = user.Id,
            Role = GroupRole.Participant,
            JoinedAt = DateTimeOffset.UtcNow
        });
        tdb.Db.News.AddRange(n1, n2);
        await tdb.Db.SaveChangesAsync();

        var svc = CreateSvc(tdb);
        Assert.Equal(2, await svc.MarkAllReadAsync(user.Id, CancellationToken.None));
        Assert.True((await svc.GetAllForUserAsync(user.Id, CancellationToken.None)).All(x => x.IsRead));
        Assert.Equal(0, await svc.MarkAllReadAsync(user.Id, CancellationToken.None));
    }

    [Fact]
    public async Task GetAllForUserAsync_and_GetByIdAsync_member_joined_news_visible_only_to_owner()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var participant = new User { Id = Guid.NewGuid(), Email = "participant@edu.hse.ru" };
        var g = new Group
        {
            Id = Guid.NewGuid(),
            Name = "Учебная группа 2026",
            Code = "aaaaaa",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var n = new News
        {
            Id = Guid.NewGuid(),
            GroupId = g.Id,
            CreatedAt = DateTimeOffset.UtcNow,
            GroupName = g.Name,
            Section = "Группы",
            Detail = "К группе присоединился новый участник",
            Type = NewsService.MemberJoinedByCodeNewsType
        };
        tdb.Db.Users.AddRange(owner, participant);
        tdb.Db.Groups.Add(g);
        tdb.Db.GroupMembers.AddRange(
            new GroupMember { GroupId = g.Id, UserId = owner.Id, Role = GroupRole.Owner, JoinedAt = DateTimeOffset.UtcNow },
            new GroupMember { GroupId = g.Id, UserId = participant.Id, Role = GroupRole.Participant, JoinedAt = DateTimeOffset.UtcNow });
        tdb.Db.News.Add(n);
        await tdb.Db.SaveChangesAsync();

        var svc = CreateSvc(tdb);
        Assert.Empty(await svc.GetAllForUserAsync(participant.Id, CancellationToken.None));
        Assert.Single(await svc.GetAllForUserAsync(owner.Id, CancellationToken.None));

        var (participantOk, _, participantErr) = await svc.GetByIdAsync(participant.Id, n.Id, CancellationToken.None);
        Assert.False(participantOk);
        Assert.Equal("forbidden", participantErr);

        var (ownerOk, _, ownerErr) = await svc.GetByIdAsync(owner.Id, n.Id, CancellationToken.None);
        Assert.True(ownerOk);
        Assert.Null(ownerErr);
    }

    [Fact]
    public async Task GetAllForUserAsync_owner_does_not_see_calendar_or_manual_sees_join_and_others_materials()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var participant = new User { Id = Guid.NewGuid(), Email = "participant@edu.hse.ru" };
        var g = new Group
        {
            Id = Guid.NewGuid(),
            Name = "BPI237",
            Code = "aaaaaa",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var nCalendar = new News
        {
            Id = Guid.NewGuid(),
            GroupId = g.Id,
            CreatedAt = DateTimeOffset.UtcNow,
            GroupName = g.Name,
            Section = "Календарь",
            Detail = "событие",
            Type = "calendar_event_created",
            ActorUserId = owner.Id
        };
        var nManual = new News
        {
            Id = Guid.NewGuid(),
            GroupId = g.Id,
            CreatedAt = DateTimeOffset.UtcNow,
            GroupName = g.Name,
            Section = "Новости",
            Detail = "ручная новость",
            Type = "manual",
            ActorUserId = owner.Id
        };
        var nJoin = new News
        {
            Id = Guid.NewGuid(),
            GroupId = g.Id,
            CreatedAt = DateTimeOffset.UtcNow,
            GroupName = g.Name,
            Section = "Группы",
            Detail = "присоединился",
            Type = NewsService.MemberJoinedByCodeNewsType,
            ActorUserId = participant.Id
        };
        var nPersonalMat = new News
        {
            Id = Guid.NewGuid(),
            GroupId = g.Id,
            CreatedAt = DateTimeOffset.UtcNow,
            GroupName = g.Name,
            Section = "Курсы",
            Detail = "материал",
            Type = NewsService.PersonalMaterialAddedNewsType,
            ActorUserId = participant.Id
        };
        var nOwnGeneralMat = new News
        {
            Id = Guid.NewGuid(),
            GroupId = g.Id,
            CreatedAt = DateTimeOffset.UtcNow,
            GroupName = g.Name,
            Section = "Курсы",
            Detail = "свой общий",
            Type = NewsService.GeneralMaterialAddedNewsType,
            ActorUserId = owner.Id
        };
        tdb.Db.Users.AddRange(owner, participant);
        tdb.Db.Groups.Add(g);
        tdb.Db.GroupMembers.AddRange(
            new GroupMember { GroupId = g.Id, UserId = owner.Id, Role = GroupRole.Owner, JoinedAt = DateTimeOffset.UtcNow },
            new GroupMember { GroupId = g.Id, UserId = participant.Id, Role = GroupRole.Participant, JoinedAt = DateTimeOffset.UtcNow });
        tdb.Db.News.AddRange(nCalendar, nManual, nJoin, nPersonalMat, nOwnGeneralMat);
        await tdb.Db.SaveChangesAsync();

        var svc = CreateSvc(tdb);
        var forOwner = await svc.GetAllForUserAsync(owner.Id, CancellationToken.None);
        Assert.Equal(2, forOwner.Count);
        Assert.Contains(forOwner, x => x.Id == nJoin.Id);
        Assert.Contains(forOwner, x => x.Id == nPersonalMat.Id);
        Assert.DoesNotContain(forOwner, x => x.Id == nCalendar.Id);
        Assert.DoesNotContain(forOwner, x => x.Id == nManual.Id);
        Assert.DoesNotContain(forOwner, x => x.Id == nOwnGeneralMat.Id);

        var forParticipant = await svc.GetAllForUserAsync(participant.Id, CancellationToken.None);
        Assert.Equal(4, forParticipant.Count);
        Assert.DoesNotContain(forParticipant, x => x.Id == nJoin.Id);
    }

    [Fact]
    public async Task GetByIdAsync_owner_forbidden_for_calendar_news()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var g = new Group
        {
            Id = Guid.NewGuid(),
            Name = "G",
            Code = "aaaaaa",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var n = new News
        {
            Id = Guid.NewGuid(),
            GroupId = g.Id,
            CreatedAt = DateTimeOffset.UtcNow,
            GroupName = g.Name,
            Section = "Календарь",
            Detail = "x",
            Type = "calendar_event_created",
            ActorUserId = owner.Id
        };
        tdb.Db.Users.Add(owner);
        tdb.Db.Groups.Add(g);
        tdb.Db.GroupMembers.Add(new GroupMember
        {
            GroupId = g.Id,
            UserId = owner.Id,
            Role = GroupRole.Owner,
            JoinedAt = DateTimeOffset.UtcNow
        });
        tdb.Db.News.Add(n);
        await tdb.Db.SaveChangesAsync();

        var svc = CreateSvc(tdb);
        var (ok, _, err) = await svc.GetByIdAsync(owner.Id, n.Id, CancellationToken.None);
        Assert.False(ok);
        Assert.Equal("forbidden", err);
    }
}
