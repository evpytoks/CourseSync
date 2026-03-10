using System.Security.Claims;
using CourseSync.Api.Controllers;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
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
                new Claim(ClaimTypes.NameIdentifier, id.ToString()),
                new Claim("sub", id.ToString())
            }, "Test"))
            : new ClaimsPrincipal();
        return controller;
    }

    [Fact]
    public async Task Create_unauthorized_when_no_user_returns_401()
    {
        await using var tdb = new TestDb();
        var controller = CreateController(new GroupService(tdb.Db), null);
        var res = await controller.Create(new CreateGroupRequest("Valid"), CancellationToken.None);
        var unauth = Assert.IsType<UnauthorizedObjectResult>(res.Result);
        Assert.Equal("unauthorized", Assert.IsType<ErrorEnvelope>(unauth.Value).Error.Code);
    }

    [Fact]
    public async Task Create_invalid_name_returns_400()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var controller = CreateController(new GroupService(tdb.Db), user.Id);
        var res = await controller.Create(new CreateGroupRequest(""), CancellationToken.None);
        var bad = Assert.IsType<BadRequestObjectResult>(res.Result);
        Assert.Equal("group_name_required", Assert.IsType<ErrorEnvelope>(bad.Value).Error.Code);
    }

    [Fact]
    public async Task Create_success_returns_200()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var controller = CreateController(new GroupService(tdb.Db), user.Id);
        var res = await controller.Create(new CreateGroupRequest("MyGroup"), CancellationToken.None);
        Assert.IsType<OkResult>(res.Result);
    }

    [Fact]
    public async Task List_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var controller = CreateController(new GroupService(tdb.Db), null);
        var res = await controller.List(CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res.Result);
    }

    [Fact]
    public async Task List_success_returns_groups()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db);
        await svc.CreateGroupAsync(user.Id, "G1", CancellationToken.None);
        var controller = CreateController(svc, user.Id);
        var res = await controller.List(CancellationToken.None);
        var ok = Assert.IsType<OkObjectResult>(res.Result);
        var payload = Assert.IsType<GroupListResponse>(ok.Value);
        Assert.Single(payload.Groups);
        Assert.Equal("G1", payload.Groups[0].Name);
        Assert.Equal("owner", payload.Groups[0].Role);
    }

    [Fact]
    public async Task Join_invalid_code_format_returns_400()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var controller = CreateController(new GroupService(tdb.Db), user.Id);
        var res = await controller.Join(new GroupJoinRequest("123"), CancellationToken.None);
        var bad = Assert.IsType<BadRequestObjectResult>(res.Result);
        Assert.Equal("invalid_code_format", Assert.IsType<ErrorEnvelope>(bad.Value).Error.Code);
    }

    [Fact]
    public async Task Join_group_not_found_returns_404()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var controller = CreateController(new GroupService(tdb.Db), user.Id);
        var res = await controller.Join(new GroupJoinRequest("ABCDEF"), CancellationToken.None);
        var notFound = Assert.IsType<NotFoundObjectResult>(res.Result);
        Assert.Equal("group_not_found", Assert.IsType<ErrorEnvelope>(notFound.Value).Error.Code);
    }

    [Fact]
    public async Task Join_success_returns_200()
    {
        await using var tdb = new TestDb();
        var owner = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "o@edu.hse.ru" };
        var joiner = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "j@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, joiner);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db);
        var create = await svc.CreateGroupAsync(owner.Id, "G", CancellationToken.None);
        Assert.NotNull(create);
        var controller = CreateController(svc, joiner.Id);
        var res = await controller.Join(new GroupJoinRequest(create.Value.Code), CancellationToken.None);
        Assert.IsType<OkResult>(res.Result);
    }

    [Fact]
    public async Task Change_forbidden_returns_403()
    {
        await using var tdb = new TestDb();
        var owner = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "o@edu.hse.ru" };
        var other = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "p@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, other);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db);
        var create = await svc.CreateGroupAsync(owner.Id, "G", CancellationToken.None);
        Assert.NotNull(create);
        await svc.JoinByCodeAsync(other.Id, create.Value.Code, CancellationToken.None);
        var controller = CreateController(svc, other.Id);
        var res = await controller.Change(create.Value.GroupId, new GroupChangeRequest("Hacked"), CancellationToken.None);
        var forbidden = Assert.IsType<ObjectResult>(res.Result);
        Assert.Equal(403, forbidden.StatusCode);
    }

    [Fact]
    public async Task Change_validation_error_returns_400()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db);
        var create = await svc.CreateGroupAsync(user.Id, "G", CancellationToken.None);
        Assert.NotNull(create);
        var controller = CreateController(svc, user.Id);
        var res = await controller.Change(create.Value.GroupId, new GroupChangeRequest("ThisNameIsWayTooLongForLimit"), CancellationToken.None);
        var bad = Assert.IsType<BadRequestObjectResult>(res.Result);
        Assert.Equal("group_name_too_long", Assert.IsType<ErrorEnvelope>(bad.Value).Error.Code);
    }

    [Fact]
    public async Task Change_success_returns_200()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db);
        var create = await svc.CreateGroupAsync(user.Id, "Old", CancellationToken.None);
        Assert.NotNull(create);
        var controller = CreateController(svc, user.Id);
        var res = await controller.Change(create.Value.GroupId, new GroupChangeRequest("NewName"), CancellationToken.None);
        Assert.IsType<OkResult>(res.Result);
    }

    [Fact]
    public async Task Choose_forbidden_when_not_member_returns_403()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        var other = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "o@edu.hse.ru" };
        tdb.Db.Users.AddRange(user, other);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db);
        var create = await svc.CreateGroupAsync(other.Id, "G", CancellationToken.None);
        Assert.NotNull(create);
        var controller = CreateController(svc, user.Id);
        var res = await controller.Choose(create.Value.GroupId, CancellationToken.None);
        Assert.Equal(403, Assert.IsType<ObjectResult>(res.Result).StatusCode);
    }

    [Fact]
    public async Task Choose_success_returns_200()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db);
        var create = await svc.CreateGroupAsync(user.Id, "Chosen", CancellationToken.None);
        Assert.NotNull(create);
        var controller = CreateController(svc, user.Id);
        var res = await controller.Choose(create.Value.GroupId, CancellationToken.None);
        Assert.IsType<OkResult>(res.Result);
    }

    [Fact]
    public async Task GetCurrent_forbidden_when_not_member_returns_403()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        var other = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "o@edu.hse.ru" };
        tdb.Db.Users.AddRange(user, other);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db);
        var create = await svc.CreateGroupAsync(other.Id, "G", CancellationToken.None);
        Assert.NotNull(create);
        var controller = CreateController(svc, user.Id);
        var res = await controller.GetCurrent(CancellationToken.None);
        var forbidden = Assert.IsType<ObjectResult>(res.Result);
        Assert.Equal(403, forbidden.StatusCode);
    }

    [Fact]
    public async Task GetCurrent_forbidden_when_not_chosen_returns_403()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db);
        var create = await svc.CreateGroupAsync(user.Id, "G", CancellationToken.None);
        Assert.NotNull(create);

        var controller = CreateController(svc, user.Id);
        var res = await controller.GetCurrent(CancellationToken.None);
        var forbidden = Assert.IsType<ObjectResult>(res.Result);
        Assert.Equal(403, forbidden.StatusCode);
    }

    [Fact]
    public async Task GetCurrent_success_returns_id_name_and_role()
    {
        await using var tdb = new TestDb();
        var user = new CourseSync.Api.Data.User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var svc = new GroupService(tdb.Db);
        var create = await svc.CreateGroupAsync(user.Id, "MyGroup", CancellationToken.None);
        Assert.NotNull(create);
        var choose = await svc.ChooseGroupAsync(user.Id, create.Value.GroupId, CancellationToken.None);
        Assert.True(choose.Ok);

        var controller = CreateController(svc, user.Id);
        var res = await controller.GetCurrent(CancellationToken.None);
        var ok = Assert.IsType<OkObjectResult>(res.Result);
        var payload = Assert.IsType<GroupDetailsResponse>(ok.Value);
        Assert.Equal(create.Value.GroupId, payload.Id);
        Assert.Equal("MyGroup", payload.Name);
        Assert.Equal("owner", payload.Role);
        Assert.NotNull(payload.GroupCode);
        Assert.Equal(6, payload.GroupCode!.Length);
    }
}
