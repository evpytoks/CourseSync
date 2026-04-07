using System.Security.Claims;
using CourseSync.Api.Controllers;
using CourseSync.Api.Data;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class SettingsControllerTests
{
    private static SettingsController CreateController(UserService users, Guid? userId)
    {
        var controller = new SettingsController(users);
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
    public async Task Get_unauthorized_when_no_user()
    {
        await using var tdb = new TestDb();
        var users = new UserService(tdb.Db);
        var controller = CreateController(users, null);

        var res = await controller.Get(CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res.Result);
    }

    [Fact]
    public async Task Get_returns_current_settings()
    {
        await using var tdb = new TestDb();
        var user = new User
        {
            Id = Guid.NewGuid(),
            Email = "user@edu.hse.ru",
            NotificationsOn = true,
            DarkThemeOn = false
        };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();

        var users = new UserService(tdb.Db);
        var controller = CreateController(users, user.Id);

        var res = await controller.Get(CancellationToken.None);
        var ok = Assert.IsType<OkObjectResult>(res.Result);
        var payload = Assert.IsType<UserSettingsResponse>(ok.Value);
        Assert.True(payload.NotificationsOn);
        Assert.False(payload.DarkThemeOn);
    }

    [Fact]
    public async Task Update_changes_only_specified_fields()
    {
        await using var tdb = new TestDb();
        var user = new User
        {
            Id = Guid.NewGuid(),
            Email = "user@edu.hse.ru",
            NotificationsOn = false,
            DarkThemeOn = false
        };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();

        var users = new UserService(tdb.Db);
        var controller = CreateController(users, user.Id);

        var req = new UpdateUserSettingsRequest(NotificationsOn: true, DarkThemeOn: null);
        var res = await controller.Update(req, CancellationToken.None);
        Assert.IsType<OkResult>(res);

        var updated = await users.FindByIdAsync(user.Id, CancellationToken.None);
        Assert.True(updated!.NotificationsOn);
        Assert.False(updated.DarkThemeOn);
    }
}

