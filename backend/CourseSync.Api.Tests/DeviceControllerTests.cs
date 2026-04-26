using System.Security.Claims;
using CourseSync.Api.Controllers;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class DeviceControllerTests
{
    private static DeviceController CreateController(UserDeviceService devices, Guid? userId)
    {
        var controller = new DeviceController(devices);
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
    public async Task Register_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var svc = new UserDeviceService(tdb.Db);
        var c = CreateController(svc, null);
        var res = await c.Register(new RegisterDeviceRequest("android", "token"), CancellationToken.None);
        ActionResultAssert.Unauthorized(res);
    }

    [Fact]
    public async Task Unregister_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var svc = new UserDeviceService(tdb.Db);
        var c = CreateController(svc, null);
        var res = await c.Unregister(new UnregisterDeviceRequest("token"), CancellationToken.None);
        ActionResultAssert.Unauthorized(res);
    }
}
