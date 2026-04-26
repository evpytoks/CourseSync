using System.Security.Claims;
using CourseSync.Api.Application.Courses;
using CourseSync.Api.Controllers;
using CourseSync.Api.Data;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class CourseControllerAuthorizationTests
{
    private static CourseController CreateController(TestDb tdb, Guid? userId)
    {
        var notifications = new NotificationService(tdb.Db);
        var blob = new NoOpCourseMaterialBlobStorage();
        var courseQuery = new CourseQueryService(tdb.Db);
        var courseCommand = new CourseCommandService(tdb.Db, notifications, blob);
        var courseGrading = new CourseGradingService(tdb.Db, notifications);
        var courseCumulative = new CourseCumulativeGradeService(tdb.Db);
        var materialSvc = new CourseMaterialService(tdb.Db, blob, notifications);
        var userSvc = new UserService(tdb.Db);
        var calendarSvc = new CalendarService(tdb.Db, notifications);
        var controller = new CourseController(courseQuery, courseCommand, courseGrading, courseCumulative, materialSvc, userSvc, calendarSvc);
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
    public async Task All_course_endpoints_return_401_when_not_authenticated()
    {
        await using var tdb = new TestDb();
        var c = CreateController(tdb, null);
        var id = Guid.NewGuid();
        var mid = Guid.NewGuid();

        ActionResultAssert.Unauthorized((await c.List(CancellationToken.None)).Result);
        ActionResultAssert.Unauthorized((await c.ListByGroup(id, CancellationToken.None)).Result);
        ActionResultAssert.Unauthorized(await c.Add(new AddCourseRequest("n", "", null, null), CancellationToken.None));
        ActionResultAssert.Unauthorized((await c.GetById(id, CancellationToken.None)).Result);
        ActionResultAssert.Unauthorized((await c.GetCalendar(id, CancellationToken.None)).Result);
        ActionResultAssert.Unauthorized(await c.SaveGrading(id, new SaveCourseGradingRequest("", null), CancellationToken.None));
        ActionResultAssert.Unauthorized(await c.GetGradingText(id, CancellationToken.None));
        ActionResultAssert.Unauthorized(await c.GradingElements(id, CancellationToken.None));
        ActionResultAssert.Unauthorized(await c.GetGrading(id, CancellationToken.None));
        ActionResultAssert.Unauthorized(await c.GetGradingScores(id, null, CancellationToken.None));
        ActionResultAssert.Unauthorized(await c.UpdateGradingScores(id, new UpdateCourseGradingScoresRequest("x", Array.Empty<decimal>()), CancellationToken.None));
        ActionResultAssert.Unauthorized(await c.SaveCumulativeGrade(id, new SaveCourseCumulativeGradeRequest(null, null, null), CancellationToken.None));
        ActionResultAssert.Unauthorized(await c.GetCumulativeGrade(id, CancellationToken.None));
        ActionResultAssert.Unauthorized(await c.Change(id, new ChangeCourseRequest("n", "", null, null), CancellationToken.None));
        ActionResultAssert.Unauthorized(await c.Delete(id, CancellationToken.None));
        ActionResultAssert.Unauthorized(await c.ListGeneralMaterials(id, CancellationToken.None));
        ActionResultAssert.Unauthorized(await c.AddGeneralMaterial(id, null, CancellationToken.None));
        ActionResultAssert.Unauthorized(await c.ListPersonalMaterials(id, CancellationToken.None));
        ActionResultAssert.Unauthorized(await c.AddPersonalMaterial(id, null, CancellationToken.None));
        ActionResultAssert.Unauthorized(await c.DeleteGeneralMaterial(id, mid, CancellationToken.None));
        ActionResultAssert.Unauthorized(await c.OpenGeneralMaterialPdf(id, mid, CancellationToken.None));
        ActionResultAssert.Unauthorized(await c.DeletePersonalMaterial(id, mid, CancellationToken.None));
        ActionResultAssert.Unauthorized(await c.OpenPersonalMaterialPdf(id, mid, CancellationToken.None));
    }

    [Fact]
    public async Task ListByGroup_non_owner_returns_403()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var participant = new User { Id = Guid.NewGuid(), Email = "participant@edu.hse.ru" };
        tdb.Db.Users.AddRange(owner, participant);
        await tdb.Db.SaveChangesAsync();
        var groupSvc = new GroupService(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());
        var created = await groupSvc.CreateGroupAsync(owner.Id, "Группа", CancellationToken.None);
        Assert.NotNull(created);
        await groupSvc.JoinByCodeAsync(participant.Id, created.Value.Code, CancellationToken.None);

        var c = CreateController(tdb, participant.Id);
        var res = await c.ListByGroup(created.Value.GroupId, CancellationToken.None);
        var forbidden = Assert.IsType<ObjectResult>(res.Result);
        Assert.Equal(403, forbidden.StatusCode);
    }
}
