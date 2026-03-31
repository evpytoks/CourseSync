using System.Security.Claims;
using CourseSync.Api.Controllers;
using CourseSync.Api.Data;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class NewsControllerTests
{
    private static NewsController CreateController(NewsService news, UserService users, Guid? userId)
    {
        var controller = new NewsController(news, users);
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
    public async Task Add_without_group_id_returns_400()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var news = new NewsService(tdb.Db, new NotificationService(tdb.Db));
        var userSvc = new UserService(tdb.Db);
        var controller = CreateController(news, userSvc, user.Id);
        var res = await controller.Add(new AddNewsRequest(Guid.Empty, "hello"), CancellationToken.None);
        var bad = Assert.IsType<BadRequestObjectResult>(res);
        Assert.Equal("group_id_required", Assert.IsType<ErrorEnvelope>(bad.Value).Error.Code);
    }

    [Fact]
    public async Task Add_as_owner_without_current_group_returns_200()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();
        var groupSvc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var created = await groupSvc.CreateGroupAsync(user.Id, "Owned", CancellationToken.None);
        Assert.NotNull(created);

        var news = new NewsService(tdb.Db, new NotificationService(tdb.Db));
        var userSvc = new UserService(tdb.Db);
        var controller = CreateController(news, userSvc, user.Id);
        var res = await controller.Add(new AddNewsRequest(created.Value.GroupId, "hello news"), CancellationToken.None);
        Assert.IsType<OkResult>(res);
    }

    [Fact]
    public async Task Add_as_participant_returns_403()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "o@edu.hse.ru" };
        var participant = new User { Id = Guid.NewGuid(), Email = "p@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, participant);
        await tdb.Db.SaveChangesAsync();
        var groupSvc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var created = await groupSvc.CreateGroupAsync(owner.Id, "G", CancellationToken.None);
        Assert.NotNull(created);
        await groupSvc.JoinByCodeAsync(participant.Id, created.Value.Code, CancellationToken.None);

        var news = new NewsService(tdb.Db, new NotificationService(tdb.Db));
        var userSvc = new UserService(tdb.Db);
        var controller = CreateController(news, userSvc, participant.Id);
        var res = await controller.Add(new AddNewsRequest(created.Value.GroupId, "x"), CancellationToken.None);
        var forbidden = Assert.IsType<ObjectResult>(res);
        Assert.Equal(403, forbidden.StatusCode);
    }
}
