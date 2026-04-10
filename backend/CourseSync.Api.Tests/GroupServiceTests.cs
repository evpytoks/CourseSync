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
    [InlineData("Матанализ", null)]
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
        var user = new User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
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
        Assert.Equal("owner@edu.hse.ru", list[0].CreatorEmail);
    }

    [Fact]
    public async Task JoinByCodeAsync_joins_and_returns_participant()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var joiner = new User { Id = Guid.NewGuid(), Email = "joiner@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, joiner);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "Линейная алгебра 2026", CancellationToken.None);
        Assert.NotNull(create);
        var result = await svc.JoinByCodeAsync(joiner.Id, create.Value.Code, CancellationToken.None);
        Assert.True(result.Ok);
        Assert.Equal("participant", result.Role);
    }

    [Fact]
    public async Task JoinByCodeAsync_already_member_returns_existing_role()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        tdb.Db.Users.Add(owner);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "Линейная алгебра 2026", CancellationToken.None);
        Assert.NotNull(create);
        var result = await svc.JoinByCodeAsync(owner.Id, create.Value.Code, CancellationToken.None);
        Assert.True(result.Ok);
        Assert.Equal("owner", result.Role);
    }

    [Fact]
    public async Task JoinByCodeAsync_invalid_code_or_unknown_group_returns_error()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var invalidFormat = await svc.JoinByCodeAsync(user.Id, "short", CancellationToken.None);
        Assert.False(invalidFormat.Ok);
        Assert.Equal("invalid_code_format", invalidFormat.ErrorCode);
        var groupNotFound = await svc.JoinByCodeAsync(user.Id, "ABCDEF", CancellationToken.None);
        Assert.False(groupNotFound.Ok);
        Assert.Equal("group_not_found", groupNotFound.ErrorCode);
    }

    [Fact]
    public async Task BlockParticipantByEmailAsync_removes_from_list_and_blocks_join_by_code()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var joiner = new User { Id = Guid.NewGuid(), Email = "joiner@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, joiner);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "Линейная алгебра 2026", CancellationToken.None);
        Assert.NotNull(create);
        await svc.JoinByCodeAsync(joiner.Id, create.Value.Code, CancellationToken.None);

        var (ok, errorCode) = await svc.BlockParticipantByEmailAsync(owner.Id, create.Value.GroupId, "joiner@edu.hse.ru", CancellationToken.None);
        Assert.True(ok);
        Assert.Null(errorCode);

        var list = await svc.GetUserGroupsAsync(joiner.Id, CancellationToken.None);
        Assert.Empty(list);

        var joinAfterBlock = await svc.JoinByCodeAsync(joiner.Id, create.Value.Code, CancellationToken.None);
        Assert.False(joinAfterBlock.Ok);
        Assert.Equal("group_join_blocked", joinAfterBlock.ErrorCode);
    }

    [Fact]
    public async Task UnblockParticipantByEmailAsync_restores_membership_and_allows_join_by_code()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var joiner = new User { Id = Guid.NewGuid(), Email = "joiner@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, joiner);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "Линейная алгебра 2026", CancellationToken.None);
        Assert.NotNull(create);
        await svc.JoinByCodeAsync(joiner.Id, create.Value.Code, CancellationToken.None);
        await svc.BlockParticipantByEmailAsync(owner.Id, create.Value.GroupId, "joiner@edu.hse.ru", CancellationToken.None);

        var (ok, errorCode) = await svc.UnblockParticipantByEmailAsync(owner.Id, create.Value.GroupId, "joiner@edu.hse.ru", CancellationToken.None);
        Assert.True(ok);
        Assert.Null(errorCode);

        var list = await svc.GetUserGroupsAsync(joiner.Id, CancellationToken.None);
        Assert.Single(list);
        Assert.Equal("participant", list[0].Role);

        var joinAfterUnblock = await svc.JoinByCodeAsync(joiner.Id, create.Value.Code, CancellationToken.None);
        Assert.True(joinAfterUnblock.Ok);
        Assert.Equal("participant", joinAfterUnblock.Role);
    }

    [Fact]
    public async Task GetParticipantEmailsForOwnerAsync_lists_active_and_blocked()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var activeParticipant = new User { Id = Guid.NewGuid(), Email = "active@edu.hse.ru" };
        var blockedParticipant = new User { Id = Guid.NewGuid(), Email = "blocked@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, activeParticipant, blockedParticipant);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "Линейная алгебра 2026", CancellationToken.None);
        Assert.NotNull(create);
        await svc.JoinByCodeAsync(activeParticipant.Id, create.Value.Code, CancellationToken.None);
        await svc.JoinByCodeAsync(blockedParticipant.Id, create.Value.Code, CancellationToken.None);
        await svc.BlockParticipantByEmailAsync(owner.Id, create.Value.GroupId, "blocked@edu.hse.ru", CancellationToken.None);

        var (ok, items, errorCode) = await svc.GetParticipantEmailsForOwnerAsync(owner.Id, create.Value.GroupId, CancellationToken.None);
        Assert.True(ok);
        Assert.Null(errorCode);
        Assert.Equal(2, items!.Count);
        var activeItem = items.Single(x => x.Email == "active@edu.hse.ru");
        Assert.False(activeItem.IsBlocked);
        var blockedItem = items.Single(x => x.Email == "blocked@edu.hse.ru");
        Assert.True(blockedItem.IsBlocked);
    }

    [Fact]
    public async Task ChangeNameAsync_owner_succeeds()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(user.Id, "СтароеИмя", CancellationToken.None);
        Assert.NotNull(create);
        var (ok, errorCode) = await svc.ChangeNameAsync(user.Id, create.Value.GroupId, "НовоеИмя", CancellationToken.None);
        Assert.True(ok);
        Assert.Null(errorCode);
        var group = await tdb.Db.Groups.SingleAsync(g => g.Id == create.Value.GroupId);
        Assert.Equal("НовоеИмя", group.Name);
    }

    [Fact]
    public async Task ChangeNameAsync_not_owner_returns_forbidden()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var other = new User { Id = Guid.NewGuid(), Email = "participant@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, other);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "Линейная алгебра 2026", CancellationToken.None);
        Assert.NotNull(create);
        await svc.JoinByCodeAsync(other.Id, create.Value.Code, CancellationToken.None);
        var (ok, errorCode) = await svc.ChangeNameAsync(other.Id, create.Value.GroupId, "ЧужоеИмя", CancellationToken.None);
        Assert.False(ok);
        Assert.Equal("forbidden", errorCode);
    }

    [Fact]
    public async Task ChangeNameAsync_invalid_name_returns_validation_error()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(user.Id, "Линейная алгебра 2026", CancellationToken.None);
        Assert.NotNull(create);
        var (ok, errorCode) = await svc.ChangeNameAsync(user.Id, create.Value.GroupId, "", CancellationToken.None);
        Assert.False(ok);
        Assert.Equal("group_name_required", errorCode);
    }

    [Fact]
    public async Task ChooseGroupAsync_member_succeeds()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(user.Id, "ВыбраннаяГруппа", CancellationToken.None);
        Assert.NotNull(create);
        var (ok, groupId, name) = await svc.ChooseGroupAsync(user.Id, create.Value.GroupId, CancellationToken.None);
        Assert.True(ok);
        Assert.Equal(create.Value.GroupId, groupId);
        Assert.Equal("ВыбраннаяГруппа", name);
    }

    [Fact]
    public async Task ChooseGroupAsync_not_member_returns_false()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        var other = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        tdb.Db.Users.AddRange(user, other);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(other.Id, "Линейная алгебра 2026", CancellationToken.None);
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
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        tdb.Db.Users.Add(owner);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "Линейная алгебра 2026", CancellationToken.None);
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
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var participant = new User { Id = Guid.NewGuid(), Email = "participant@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, participant);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "Линейная алгебра 2026", CancellationToken.None);
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
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        tdb.Db.Users.Add(owner);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());

        var (ok, errorCode) = await svc.DeleteGroupAsync(owner.Id, Guid.NewGuid(), CancellationToken.None);
        Assert.False(ok);
        Assert.Equal("group_not_found", errorCode);
    }
}
