using System.Security.Claims;
using CourseSync.Api.Controllers;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class MeControllerTests
{
    private static MeController CreateController(GroupService service, Guid? userId)
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
    public async Task GetCurrentGroup_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var controller = CreateController(
            new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()),
            null);
        var res = await controller.GetCurrentGroup(CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res.Result);
    }

    [Fact]
    public async Task SetCurrentGroup_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var controller = CreateController(
            new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage()),
            null);
        var res = await controller.SetCurrentGroup(new SetCurrentGroupRequest(Guid.NewGuid()), CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res);
    }
}
