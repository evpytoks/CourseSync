using System.Security.Claims;
using CourseSync.Api.Controllers;
using CourseSync.Api.Data;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class CourseControllerTests
{
    private static CourseController CreateController(TestDb tdb, Guid? userId)
    {
        var notifications = new NotificationService(tdb.Db);
        var blob = new NoOpCourseMaterialBlobStorage();
        var courseSvc = new CourseService(tdb.Db, notifications, blob);
        var materialSvc = new CourseMaterialService(tdb.Db, blob, notifications);
        var userSvc = new UserService(tdb.Db);
        var controller = new CourseController(courseSvc, materialSvc, userSvc);
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
    public async Task Delete_unauthorized_returns_401()
    {
        await using var tdb = new TestDb();
        var controller = CreateController(tdb, null);
        var res = await controller.Delete(Guid.NewGuid(), CancellationToken.None);
        Assert.IsType<UnauthorizedObjectResult>(res);
    }

    [Fact]
    public async Task Delete_no_current_group_returns_400()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "u@edu.hse.ru" };
        tdb.Db.Users.Add(user);
        await tdb.Db.SaveChangesAsync();

        var controller = CreateController(tdb, user.Id);
        var res = await controller.Delete(Guid.NewGuid(), CancellationToken.None);
        var bad = Assert.IsType<BadRequestObjectResult>(res);
        Assert.Equal("no_group_selected", Assert.IsType<ErrorEnvelope>(bad.Value).Error.Code);
    }

    [Fact]
    public async Task Delete_owner_returns_204()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "o@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "G",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "C",
            GeneralInfo = "x",
            UsefulLinks = "y",
            CreatedAt = DateTimeOffset.UtcNow
        };
        tdb.Db.Users.Add(owner);
        tdb.Db.Groups.Add(group);
        tdb.Db.GroupMembers.Add(new GroupMember
        {
            GroupId = group.Id,
            UserId = owner.Id,
            Role = GroupRole.Owner,
            JoinedAt = DateTimeOffset.UtcNow
        });
        tdb.Db.Courses.Add(course);
        owner.CurrentGroupId = group.Id;
        await tdb.Db.SaveChangesAsync();

        var controller = CreateController(tdb, owner.Id);
        var res = await controller.Delete(course.Id, CancellationToken.None);
        Assert.IsType<NoContentResult>(res);
    }

    [Fact]
    public async Task Delete_participant_returns_403()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "o@edu.hse.ru" };
        var participant = new User { Id = Guid.NewGuid(), Email = "p@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "G",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "C",
            GeneralInfo = "x",
            UsefulLinks = "y",
            CreatedAt = DateTimeOffset.UtcNow
        };
        tdb.Db.Users.AddRange(owner, participant);
        tdb.Db.Groups.Add(group);
        tdb.Db.GroupMembers.AddRange(
            new GroupMember
            {
                GroupId = group.Id,
                UserId = owner.Id,
                Role = GroupRole.Owner,
                JoinedAt = DateTimeOffset.UtcNow
            },
            new GroupMember
            {
                GroupId = group.Id,
                UserId = participant.Id,
                Role = GroupRole.Participant,
                JoinedAt = DateTimeOffset.UtcNow
            });
        tdb.Db.Courses.Add(course);
        participant.CurrentGroupId = group.Id;
        await tdb.Db.SaveChangesAsync();

        var controller = CreateController(tdb, participant.Id);
        var res = await controller.Delete(course.Id, CancellationToken.None);
        var forbidden = Assert.IsType<ObjectResult>(res);
        Assert.Equal(403, forbidden.StatusCode);
    }

    [Fact]
    public async Task Delete_unknown_course_returns_400()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "o@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "G",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        tdb.Db.Users.Add(owner);
        tdb.Db.Groups.Add(group);
        tdb.Db.GroupMembers.Add(new GroupMember
        {
            GroupId = group.Id,
            UserId = owner.Id,
            Role = GroupRole.Owner,
            JoinedAt = DateTimeOffset.UtcNow
        });
        owner.CurrentGroupId = group.Id;
        await tdb.Db.SaveChangesAsync();

        var controller = CreateController(tdb, owner.Id);
        var res = await controller.Delete(Guid.NewGuid(), CancellationToken.None);
        var bad = Assert.IsType<BadRequestObjectResult>(res);
        Assert.Equal("course_not_in_group", Assert.IsType<ErrorEnvelope>(bad.Value).Error.Code);
    }
}
