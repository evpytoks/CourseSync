using System.Security.Claims;
using CourseSync.Api.Controllers;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class CalendarControllerTests
{
    private static CalendarController CreateController(CalendarService calendar, UserService users, Guid? userId)
    {
        var controller = new CalendarController(calendar, users);
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
    public async Task List_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var cal = new CalendarService(tdb.Db, new NotificationService(tdb.Db));
        var users = new UserService(tdb.Db);
        var c = CreateController(cal, users, null);
        var res = await c.List(null, null, CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res.Result);
    }

    [Fact]
    public async Task EventTypes_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var cal = new CalendarService(tdb.Db, new NotificationService(tdb.Db));
        var users = new UserService(tdb.Db);
        var c = CreateController(cal, users, null);
        var res = await c.EventTypes(CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res.Result);
    }

    [Fact]
    public async Task Add_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var cal = new CalendarService(tdb.Db, new NotificationService(tdb.Db));
        var users = new UserService(tdb.Db);
        var c = CreateController(cal, users, null);
        var req = new AddCalendarEventRequest(Guid.NewGuid(), null, "type", "name", DateTime.UtcNow, "");
        var res = await c.Add(req, CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res);
    }

    [Fact]
    public async Task Get_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var cal = new CalendarService(tdb.Db, new NotificationService(tdb.Db));
        var users = new UserService(tdb.Db);
        var c = CreateController(cal, users, null);
        var res = await c.Get(Guid.NewGuid(), CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res.Result);
    }

    [Fact]
    public async Task Change_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var cal = new CalendarService(tdb.Db, new NotificationService(tdb.Db));
        var users = new UserService(tdb.Db);
        var c = CreateController(cal, users, null);
        var req = new UpdateCalendarEventRequest(null, "t", "n", DateTime.UtcNow, "");
        var res = await c.Change(Guid.NewGuid(), req, CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res);
    }

    [Fact]
    public async Task Delete_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var cal = new CalendarService(tdb.Db, new NotificationService(tdb.Db));
        var users = new UserService(tdb.Db);
        var c = CreateController(cal, users, null);
        var res = await c.Delete(Guid.NewGuid(), CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res);
    }

    [Fact]
    public async Task ToggleDone_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var cal = new CalendarService(tdb.Db, new NotificationService(tdb.Db));
        var users = new UserService(tdb.Db);
        var c = CreateController(cal, users, null);
        var res = await c.ToggleDone(Guid.NewGuid(), CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res);
    }
}
