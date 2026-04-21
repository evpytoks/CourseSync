using System.Security.Claims;
using CourseSync.Api.Controllers;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.EntityFrameworkCore;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class GroupControllerTests
{
    private static GroupController CreateController(GroupService service, Guid? userId)
    {
        var controller = new GroupController(service);
        controller.ControllerContext = new ControllerContext
        {
            HttpContext = new DefaultHttpContext(),
            RouteData = new Microsoft.AspNetCore.Routing.RouteData()
        };
        controller.HttpContext.User = userId is { } id
            ? new ClaimsPrincipal(new ClaimsIdentity(new[]
            {
                new Claim(ClaimTypes.NameIdentifier, id.ToString())
            }, "Test"))
            : new ClaimsPrincipal();
        return controller;
    }

    private static MeController CreateMeController(GroupService service, Guid? userId)
    {
        var controller = new MeController(service);
        controller.ControllerContext = new ControllerContext
        {
            HttpContext = new DefaultHttpContext(),
            RouteData = new Microsoft.AspNetCore.Routing.RouteData()
        };
        controller.HttpContext.User = userId is { } id
            ? new ClaimsPrincipal(new ClaimsIdentity(new[]
            {
                new Claim(ClaimTypes.NameIdentifier, id.ToString())
            }, "Test"))
            : new ClaimsPrincipal();
        return controller;
    }

    [Fact]
    public async Task Create_unauthorized_when_no_user_returns_401()
    {
        await using var tdb = new TestDb();
        var controller = CreateController(new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()), null);
        var res = await controller.Create(new CreateGroupRequest("Valid"), CancellationToken.None);
        var unauth = Assert.IsType<UnauthorizedObjectResult>(res);
        Assert.Equal("unauthorized", Assert.IsType<ErrorEnvelope>(unauth.Value).Error.Code);
    }

    [Fact]
    public async Task Create_invalid_name_returns_400()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var controller = CreateController(new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()), user.Id);
        var res = await controller.Create(new CreateGroupRequest(""), CancellationToken.None);
        var bad = Assert.IsType<BadRequestObjectResult>(res);
        Assert.Equal("group_name_required", Assert.IsType<ErrorEnvelope>(bad.Value).Error.Code);
    }

    [Fact]
    public async Task Create_success_returns_200()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var controller = CreateController(new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()), user.Id);
        var res = await controller.Create(new CreateGroupRequest("МояГруппа"), CancellationToken.None);
        Assert.IsType<OkResult>(res);
    }

    [Fact]
    public async Task List_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var controller = CreateController(new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()), null);
        var res = await controller.List(CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res.Result);
    }

    [Fact]
    public async Task List_success_returns_groups()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        await svc.CreateGroupAsync(user.Id, "G1", CancellationToken.None);
        var controller = CreateController(svc, user.Id);
        var res = await controller.List(CancellationToken.None);
        var ok = Assert.IsType<OkObjectResult>(res.Result);
        var payload = Assert.IsType<GroupListResponse>(ok.Value);
        Assert.Single(payload.Groups);
        Assert.Equal("G1", payload.Groups[0].Name);
        Assert.Equal("owner", payload.Groups[0].Role);
        Assert.Equal("user@edu.hse.ru", payload.Groups[0].CreatorEmail);
    }

    [Fact]
    public async Task OwnerList_returns_only_groups_where_user_is_owner()
    {
        await using var tdb = new TestDb();
        var owner = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var joiner = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "joiner@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, joiner);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var gСвояГруппа = await svc.CreateGroupAsync(owner.Id, "СвояГруппа", CancellationToken.None);
        var gЧужаяГруппа = await svc.CreateGroupAsync(joiner.Id, "ЧужаяГруппа", CancellationToken.None);
        Assert.NotNull(gСвояГруппа);
        Assert.NotNull(gЧужаяГруппа);
        await svc.JoinByCodeAsync(owner.Id, gЧужаяГруппа.Value.Code, CancellationToken.None);

        var controller = CreateController(svc, owner.Id);
        var res = await controller.OwnerList(CancellationToken.None);
        var ok = Assert.IsType<OkObjectResult>(res.Result);
        var payload = Assert.IsType<OwnerGroupListResponse>(ok.Value);
        Assert.Single(payload.Groups);
        Assert.Equal("СвояГруппа", payload.Groups[0].Name);
    }

    [Fact]
    public async Task Join_invalid_code_format_returns_400()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var controller = CreateController(new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()), user.Id);
        var res = await controller.Join(new GroupJoinRequest("123"), CancellationToken.None);
        var bad = Assert.IsType<BadRequestObjectResult>(res);
        Assert.Equal("invalid_code_format", Assert.IsType<ErrorEnvelope>(bad.Value).Error.Code);
    }

    [Fact]
    public async Task Join_group_not_found_returns_404()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var controller = CreateController(new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()), user.Id);
        var res = await controller.Join(new GroupJoinRequest("ABCDEF"), CancellationToken.None);
        var notFound = Assert.IsType<NotFoundObjectResult>(res);
        Assert.Equal("group_not_found", Assert.IsType<ErrorEnvelope>(notFound.Value).Error.Code);
    }

    [Fact]
    public async Task Join_success_returns_200()
    {
        await using var tdb = new TestDb();
        var owner = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var joiner = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "joiner@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, joiner);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "Линейная алгебра 2026", CancellationToken.None);
        Assert.NotNull(create);
        var controller = CreateController(svc, joiner.Id);
        var res = await controller.Join(new GroupJoinRequest(create.Value.Code), CancellationToken.None);
        Assert.IsType<OkResult>(res);
    }

    [Fact]
    public async Task Change_forbidden_returns_403()
    {
        await using var tdb = new TestDb();
        var owner = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var other = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "participant@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, other);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "Линейная алгебра 2026", CancellationToken.None);
        Assert.NotNull(create);
        await svc.JoinByCodeAsync(other.Id, create.Value.Code, CancellationToken.None);
        var controller = CreateController(svc, other.Id);
        var res = await controller.Change(create.Value.GroupId, new GroupChangeRequest("ЧужоеИмя"), CancellationToken.None);
        var forbidden = Assert.IsType<ObjectResult>(res);
        Assert.Equal(403, forbidden.StatusCode);
    }

    [Fact]
    public async Task Change_validation_error_returns_400()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(user.Id, "Линейная алгебра 2026", CancellationToken.None);
        Assert.NotNull(create);
        var controller = CreateController(svc, user.Id);
        var res = await controller.Change(create.Value.GroupId, new GroupChangeRequest("ThisNameIsWayTooLongForLimit"), CancellationToken.None);
        var bad = Assert.IsType<BadRequestObjectResult>(res);
        Assert.Equal("group_name_too_long", Assert.IsType<ErrorEnvelope>(bad.Value).Error.Code);
    }

    [Fact]
    public async Task Change_success_returns_200()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(user.Id, "СтароеИмя", CancellationToken.None);
        Assert.NotNull(create);
        var controller = CreateController(svc, user.Id);
        var res = await controller.Change(create.Value.GroupId, new GroupChangeRequest("НовоеИмя"), CancellationToken.None);
        Assert.IsType<OkResult>(res);
    }

    [Fact]
    public async Task Choose_forbidden_when_not_member_returns_403()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        var other = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        tdb.Db.Users.AddRange(user, other);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(other.Id, "Линейная алгебра 2026", CancellationToken.None);
        Assert.NotNull(create);
        var me = CreateMeController(svc, user.Id);
        var res = await me.SetCurrentGroup(new SetCurrentGroupRequest(create.Value.GroupId), CancellationToken.None);
        Assert.Equal(403, Assert.IsType<ObjectResult>(res).StatusCode);
    }

    [Fact]
    public async Task Choose_success_returns_200()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(user.Id, "ВыбраннаяГруппа", CancellationToken.None);
        Assert.NotNull(create);
        var me = CreateMeController(svc, user.Id);
        var res = await me.SetCurrentGroup(new SetCurrentGroupRequest(create.Value.GroupId), CancellationToken.None);
        Assert.IsType<OkResult>(res);
    }

    [Fact]
    public async Task GetCurrent_without_chosen_group_returns_400_no_group_selected()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        var other = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        tdb.Db.Users.AddRange(user, other);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(other.Id, "Линейная алгебра 2026", CancellationToken.None);
        Assert.NotNull(create);
        var me = CreateMeController(svc, user.Id);
        var res = await me.GetCurrentGroup(CancellationToken.None);
        var bad = Assert.IsType<BadRequestObjectResult>(res.Result);
        Assert.Equal("no_group_selected", Assert.IsType<ErrorEnvelope>(bad.Value).Error.Code);
    }

    [Fact]
    public async Task GetCurrent_when_not_chosen_returns_400_no_group_selected()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(user.Id, "Линейная алгебра 2026", CancellationToken.None);
        Assert.NotNull(create);

        var me = CreateMeController(svc, user.Id);
        var res = await me.GetCurrentGroup(CancellationToken.None);
        var bad = Assert.IsType<BadRequestObjectResult>(res.Result);
        Assert.Equal("no_group_selected", Assert.IsType<ErrorEnvelope>(bad.Value).Error.Code);
    }

    [Fact]
    public async Task GetCurrent_success_returns_id_name_and_role()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(user.Id, "МояГруппа", CancellationToken.None);
        Assert.NotNull(create);
        var choose = await svc.ChooseGroupAsync(user.Id, create.Value.GroupId, CancellationToken.None);
        Assert.True(choose.Ok);

        var me = CreateMeController(svc, user.Id);
        var res = await me.GetCurrentGroup(CancellationToken.None);
        var ok = Assert.IsType<OkObjectResult>(res.Result);
        var payload = Assert.IsType<GroupDetailsResponse>(ok.Value);
        Assert.Equal(create.Value.GroupId, payload.Id);
        Assert.Equal("МояГруппа", payload.Name);
        Assert.Equal("owner", payload.Role);
        Assert.NotNull(payload.GroupCode);
        Assert.Equal(6, payload.GroupCode!.Length);
    }

    [Fact]
    public async Task Delete_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var controller = CreateController(
            new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()),
            null);
        var res = await controller.Delete(Guid.NewGuid(), CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res);
    }

    [Fact]
    public async Task Delete_owner_returns_204()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(user.Id, "Линейная алгебра 2026", CancellationToken.None);
        Assert.NotNull(create);

        var controller = CreateController(svc, user.Id);
        var res = await controller.Delete(create.Value.GroupId, CancellationToken.None);
        Assert.IsType<NoContentResult>(res);
    }

    [Fact]
    public async Task Delete_participant_returns_403()
    {
        await using var tdb = new TestDb();
        var owner = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var participant = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "participant@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, participant);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "Линейная алгебра 2026", CancellationToken.None);
        Assert.NotNull(create);
        await svc.JoinByCodeAsync(participant.Id, create.Value.Code, CancellationToken.None);

        var controller = CreateController(svc, participant.Id);
        var res = await controller.Delete(create.Value.GroupId, CancellationToken.None);
        var forbidden = Assert.IsType<ObjectResult>(res);
        Assert.Equal(403, forbidden.StatusCode);
    }

    [Fact]
    public async Task Delete_unknown_group_returns_404()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var controller = CreateController(
            new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()),
            user.Id);
        var res = await controller.Delete(Guid.NewGuid(), CancellationToken.None);
        Assert.IsType<NotFoundObjectResult>(res);
    }

    [Fact]
    public async Task Leave_not_in_group_returns_404()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var controller = CreateController(
            new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()),
            user.Id);
        var res = await controller.Leave(Guid.NewGuid(), CancellationToken.None);
        Assert.IsType<NotFoundObjectResult>(res);
    }

    [Fact]
    public async Task Leave_participant_removes_membership_and_clears_current_group()
    {
        await using var tdb = new TestDb();
        var owner = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var participant = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "participant@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, participant);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "Линейная алгебра 2026", CancellationToken.None);
        Assert.NotNull(create);
        await svc.JoinByCodeAsync(participant.Id, create.Value.Code, CancellationToken.None);
        await svc.ChooseGroupAsync(participant.Id, create.Value.GroupId, CancellationToken.None);

        var controller = CreateController(svc, participant.Id);
        var res = await controller.Leave(create.Value.GroupId, CancellationToken.None);
        Assert.IsType<NoContentResult>(res);

        var p = await tdb.Db.Users.AsNoTracking().FirstAsync(u => u.Id == participant.Id);
        Assert.Null(p.CurrentGroupId);
        Assert.False(await tdb.Db.GroupMembers.AnyAsync(m => m.GroupId == create.Value.GroupId && m.UserId == participant.Id));
    }

    [Fact]
    public async Task Leave_participant_keeps_current_group_when_other_group_selected()
    {
        await using var tdb = new TestDb();
        var owner = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var participant = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "participant@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, participant);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var g1 = await svc.CreateGroupAsync(owner.Id, "Дискретная математика 2026", CancellationToken.None);
        var g2 = await svc.CreateGroupAsync(participant.Id, "Физика 2026", CancellationToken.None);
        Assert.NotNull(g1);
        Assert.NotNull(g2);
        await svc.JoinByCodeAsync(participant.Id, g1.Value.Code, CancellationToken.None);
        await svc.ChooseGroupAsync(participant.Id, g2.Value.GroupId, CancellationToken.None);

        var controller = CreateController(svc, participant.Id);
        var res = await controller.Leave(g1.Value.GroupId, CancellationToken.None);
        Assert.IsType<NoContentResult>(res);

        var p = await tdb.Db.Users.AsNoTracking().FirstAsync(u => u.Id == participant.Id);
        Assert.Equal(g2.Value.GroupId, p.CurrentGroupId);
    }

    [Fact]
    public async Task Leave_owner_deletes_group_returns_204()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(user.Id, "Линейная алгебра 2026", CancellationToken.None);
        Assert.NotNull(create);

        var controller = CreateController(svc, user.Id);
        var res = await controller.Leave(create.Value.GroupId, CancellationToken.None);
        Assert.IsType<NoContentResult>(res);
        Assert.False(await tdb.Db.Groups.AnyAsync(g => g.Id == create.Value.GroupId));
    }

    [Fact]
    public async Task OwnerList_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var controller = CreateController(
            new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()),
            null);
        var res = await controller.OwnerList(CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res.Result);
    }

    [Fact]
    public async Task Join_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var controller = CreateController(
            new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()),
            null);
        var res = await controller.Join(new GroupJoinRequest("ABCDEF"), CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res);
    }

    [Fact]
    public async Task Change_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var controller = CreateController(
            new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()),
            null);
        var res = await controller.Change(Guid.NewGuid(), new GroupChangeRequest("x"), CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res);
    }

    [Fact]
    public async Task Leave_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var controller = CreateController(
            new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()),
            null);
        var res = await controller.Leave(Guid.NewGuid(), CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res);
    }

    [Fact]
    public async Task GetParticipants_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var controller = CreateController(
            new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()),
            null);
        var res = await controller.GetParticipants(Guid.NewGuid(), CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res.Result);
    }

    [Fact]
    public async Task GetParticipants_non_owner_returns_403()
    {
        await using var tdb = new TestDb();
        var owner = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var participant = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "participant@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, participant);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "Группа", CancellationToken.None);
        Assert.NotNull(create);
        await svc.JoinByCodeAsync(participant.Id, create.Value.Code, CancellationToken.None);

        var controller = CreateController(svc, participant.Id);
        var res = await controller.GetParticipants(create.Value.GroupId, CancellationToken.None);
        var forbidden = Assert.IsType<ObjectResult>(res.Result);
        Assert.Equal(403, forbidden.StatusCode);
    }

    [Fact]
    public async Task BlockParticipant_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var controller = CreateController(
            new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()),
            null);
        var res = await controller.BlockParticipant(Guid.NewGuid(), new GroupParticipantEmailRequest("a@b.ru"), CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res);
    }

    [Fact]
    public async Task BlockParticipant_non_owner_returns_403()
    {
        await using var tdb = new TestDb();
        var owner = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var participant = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "participant@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, participant);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "Группа", CancellationToken.None);
        Assert.NotNull(create);
        await svc.JoinByCodeAsync(participant.Id, create.Value.Code, CancellationToken.None);

        var controller = CreateController(svc, participant.Id);
        var res = await controller.BlockParticipant(create.Value.GroupId, new GroupParticipantEmailRequest("owner@edu.hse.ru"), CancellationToken.None);
        var forbidden = Assert.IsType<ObjectResult>(res);
        Assert.Equal(403, forbidden.StatusCode);
    }

    [Fact]
    public async Task UnblockParticipant_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var controller = CreateController(
            new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()),
            null);
        var res = await controller.UnblockParticipant(Guid.NewGuid(), "a@b.ru", CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res);
    }

    [Fact]
    public async Task UnblockParticipant_non_owner_returns_403()
    {
        await using var tdb = new TestDb();
        var owner = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var participant = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "participant@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, participant);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "Группа", CancellationToken.None);
        Assert.NotNull(create);
        await svc.JoinByCodeAsync(participant.Id, create.Value.Code, CancellationToken.None);

        var controller = CreateController(svc, participant.Id);
        var res = await controller.UnblockParticipant(create.Value.GroupId, "owner@edu.hse.ru", CancellationToken.None);
        var forbidden = Assert.IsType<ObjectResult>(res);
        Assert.Equal(403, forbidden.StatusCode);
    }

    [Fact]
    public async Task Join_when_blocked_returns_403()
    {
        await using var tdb = new TestDb();
        var owner = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var joiner = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "joiner@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, joiner);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var create = await svc.CreateGroupAsync(owner.Id, "Группа", CancellationToken.None);
        Assert.NotNull(create);
        await svc.JoinByCodeAsync(joiner.Id, create.Value.Code, CancellationToken.None);
        await svc.BlockParticipantByEmailAsync(owner.Id, create.Value.GroupId, "joiner@edu.hse.ru", CancellationToken.None);

        var controller = CreateController(svc, joiner.Id);
        var res = await controller.Join(new GroupJoinRequest(create.Value.Code), CancellationToken.None);
        var forbidden = Assert.IsType<ObjectResult>(res);
        Assert.Equal(403, forbidden.StatusCode);
        Assert.Equal("group_join_blocked", Assert.IsType<ErrorEnvelope>(forbidden.Value).Error.Code);
    }
}
