using CourseSync.Api.Data;
using CourseSync.Api.Services;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class GroupServiceTests
{
    [Theory]
    [InlineData(null, "group_name_required")]
    [InlineData("", "group_name_required")]
    [InlineData("   ", "group_name_required")]
    [InlineData("a", null)]
    [InlineData("GroupName", null)]
    [InlineData("Группа", null)]
    [InlineData("12345678901234567890", null)]
    [InlineData("123456789012345678901", "group_name_too_long")]
    [InlineData("inv@lid", "group_name_invalid")]
    public void ValidateGroupName_returns_expected(string? name, string? expectedError)
    {
        var (valid, errorCode) = GroupService.ValidateGroupName(name);
        if (expectedError is null) { Assert.True(valid); Assert.Null(errorCode); }
        else { Assert.False(valid); Assert.Equal(expectedError, errorCode); }
    }

    [Theory]
    [InlineData(null, "invalid_code_format")]
    [InlineData("12345", "invalid_code_format")]
    [InlineData("1234567", "invalid_code_format")]
    [InlineData("12345!", "invalid_code_format")]
    [InlineData("ABCDEF", null)]
    [InlineData("abc123", null)]
    public void ValidateGroupCode_returns_expected(string? code, string? expectedError)
    {
        var (valid, errorCode) = GroupService.ValidateGroupCode(code);
        if (expectedError is null) { Assert.True(valid); Assert.Null(errorCode); }
        else { Assert.False(valid); Assert.Equal(expectedError, errorCode); }
    }

    [Fact]
    public async Task CreateGroupAsync_creates_group_and_returns_result()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var result = await svc.CreateGroupAsync(user.Id, "My Group", CancellationToken.None);
        Assert.NotNull(result);
        Assert.NotEqual(Guid.Empty, result.Value.GroupId);
        Assert.Equal("My Group", result.Value.Name);
        Assert.Equal(6, result.Value.Code.Length);
        var group = await tdb.Db.Groups.SingleOrDefaultAsync(g => g.Id == result.Value.GroupId);
        Assert.NotNull(group);
        var member = await tdb.Db.GroupMembers.SingleOrDefaultAsync(m => m.GroupId == group.Id && m.UserId == user.Id);
        Assert.NotNull(member);
        Assert.Equal(GroupRole.Owner, member.Role);
    }

    [Fact]
    public async Task GetUserGroupsAsync_returns_empty_when_no_memberships()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var list = await new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()).GetUserGroupsAsync(user.Id, CancellationToken.None);
        Assert.Empty(list);
    }

    [Fact]
    public async Task GetUserGroupsAsync_returns_groups_with_code_for_owner()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(user.Id, "G1", CancellationToken.None);
        Assert.NotNull(create);
        var list = await svc.GetUserGroupsAsync(user.Id, CancellationToken.None);
        Assert.Single(list);
        Assert.Equal("G1", list[0].Name);
        Assert.Equal("owner", list[0].Role);
        Assert.NotNull(list[0].GroupCode);
        Assert.Equal(6, list[0].GroupCode!.Length);
    }

    [Fact]
    public async Task JoinByCodeAsync_joins_and_returns_participant()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "o@edu.hse.ru" };
        var joiner = new User { Id = Guid.NewGuid(), Email = "j@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, joiner);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "G", CancellationToken.None);
        Assert.NotNull(create);
        var result = await svc.JoinByCodeAsync(joiner.Id, create.Value.Code, CancellationToken.None);
        Assert.NotNull(result);
        Assert.Equal("participant", result.Value.Role);
    }

    [Fact]
    public async Task JoinByCodeAsync_already_member_returns_existing_role()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "o@edu.hse.ru" };
        tdb.Db.Users.Add(owner);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "G", CancellationToken.None);
        Assert.NotNull(create);
        var result = await svc.JoinByCodeAsync(owner.Id, create.Value.Code, CancellationToken.None);
        Assert.NotNull(result);
        Assert.Equal("owner", result.Value.Role);
    }

    [Fact]
    public async Task JoinByCodeAsync_invalid_code_returns_null()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        Assert.Null(await svc.JoinByCodeAsync(user.Id, "short", CancellationToken.None));
        Assert.Null(await svc.JoinByCodeAsync(user.Id, "ABCDEF", CancellationToken.None));
    }

    [Fact]
    public async Task ChangeNameAsync_owner_succeeds()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "o@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(user.Id, "Old", CancellationToken.None);
        Assert.NotNull(create);
        var (ok, errorCode) = await svc.ChangeNameAsync(user.Id, create.Value.GroupId, "NewName", CancellationToken.None);
        Assert.True(ok);
        Assert.Null(errorCode);
        var group = await tdb.Db.Groups.SingleAsync(g => g.Id == create.Value.GroupId);
        Assert.Equal("NewName", group.Name);
    }

    [Fact]
    public async Task ChangeNameAsync_not_owner_returns_forbidden()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "o@edu.hse.ru" };
        var other = new User { Id = Guid.NewGuid(), Email = "p@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, other);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "G", CancellationToken.None);
        Assert.NotNull(create);
        await svc.JoinByCodeAsync(other.Id, create.Value.Code, CancellationToken.None);
        var (ok, errorCode) = await svc.ChangeNameAsync(other.Id, create.Value.GroupId, "Hacked", CancellationToken.None);
        Assert.False(ok);
        Assert.Equal("forbidden", errorCode);
    }

    [Fact]
    public async Task ChangeNameAsync_invalid_name_returns_validation_error()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "o@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(user.Id, "G", CancellationToken.None);
        Assert.NotNull(create);
        var (ok, errorCode) = await svc.ChangeNameAsync(user.Id, create.Value.GroupId, "", CancellationToken.None);
        Assert.False(ok);
        Assert.Equal("group_name_required", errorCode);
    }

    [Fact]
    public async Task ChooseGroupAsync_member_succeeds()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(user.Id, "Chosen", CancellationToken.None);
        Assert.NotNull(create);
        var (ok, groupId, name) = await svc.ChooseGroupAsync(user.Id, create.Value.GroupId, CancellationToken.None);
        Assert.True(ok);
        Assert.Equal(create.Value.GroupId, groupId);
        Assert.Equal("Chosen", name);
    }

    [Fact]
    public async Task ChooseGroupAsync_not_member_returns_false()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        var other = new User { Id = Guid.NewGuid(), Email = "o@edu.hse.ru" };
        tdb.Db.Users.AddRange(user, other);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(other.Id, "G", CancellationToken.None);
        Assert.NotNull(create);
        var (ok, groupId, name) = await svc.ChooseGroupAsync(user.Id, create.Value.GroupId, CancellationToken.None);
        Assert.False(ok);
        Assert.Null(groupId);
        Assert.Null(name);
    }

    [Fact]
    public async Task DeleteGroupAsync_owner_removes_group_and_clears_current_group()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "o@edu.hse.ru" };
        tdb.Db.Users.Add(owner);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "G", CancellationToken.None);
        Assert.NotNull(create);
        var groupId = create.Value.GroupId;
        owner.CurrentGroupId = groupId;
        await tdb.Db.SaveChangesAsync();

        var (ok, errorCode) = await svc.DeleteGroupAsync(owner.Id, groupId, CancellationToken.None);
        Assert.True(ok);
        Assert.Null(errorCode);
        Assert.False(await tdb.Db.Groups.AnyAsync(g => g.Id == groupId));
        var reloaded = await tdb.Db.Users.AsNoTracking().FirstAsync(u => u.Id == owner.Id);
        Assert.Null(reloaded.CurrentGroupId);
    }

    [Fact]
    public async Task DeleteGroupAsync_participant_forbidden()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "o@edu.hse.ru" };
        var participant = new User { Id = Guid.NewGuid(), Email = "p@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, participant);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "G", CancellationToken.None);
        Assert.NotNull(create);
        await svc.JoinByCodeAsync(participant.Id, create.Value.Code, CancellationToken.None);

        var (ok, errorCode) = await svc.DeleteGroupAsync(participant.Id, create.Value.GroupId, CancellationToken.None);
        Assert.False(ok);
        Assert.Equal("forbidden", errorCode);
        Assert.True(await tdb.Db.Groups.AnyAsync(g => g.Id == create.Value.GroupId));
    }

    [Fact]
    public async Task DeleteGroupAsync_unknown_group_not_found()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "o@edu.hse.ru" };
        tdb.Db.Users.Add(owner);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());

        var (ok, errorCode) = await svc.DeleteGroupAsync(owner.Id, Guid.NewGuid(), CancellationToken.None);
        Assert.False(ok);
        Assert.Equal("group_not_found", errorCode);
    }
}
