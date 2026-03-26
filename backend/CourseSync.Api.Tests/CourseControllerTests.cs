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
        var user = new User { Id = Guid.NewGuid(), Email = "student@edu.hse.ru" };
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
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "MathGroup2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Linear Algebra",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = "https://example.edu/algebra",
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
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var participant = new User { Id = Guid.NewGuid(), Email = "participant@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "MathGroup2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Linear Algebra",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = "https://example.edu/algebra",
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
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "MathGroup2026",
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

    [Fact]
    public async Task OpenGeneralPdf_owner_returns_file()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "MathGroup2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Linear Algebra",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = "https://example.edu/algebra",
            CreatedAt = DateTimeOffset.UtcNow
        };
        var materialId = Guid.NewGuid();
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
        tdb.Db.CourseGeneralMaterials.Add(new CourseGeneralMaterial
        {
            Id = materialId,
            CourseId = course.Id,
            Name = "lecture-notes.pdf",
            AuthorUserId = owner.Id,
            AuthorEmail = owner.Email,
            StoragePath = "general/path",
            CreatedAt = DateTimeOffset.UtcNow
        });
        owner.CurrentGroupId = group.Id;
        await tdb.Db.SaveChangesAsync();

        var controller = CreateController(tdb, owner.Id);
        var res = await controller.OpenGeneralMaterialPdf(course.Id, materialId, CancellationToken.None);
        var file = Assert.IsType<FileStreamResult>(res);
        Assert.Equal("application/pdf", file.ContentType);
        Assert.Equal("lecture-notes.pdf", file.FileDownloadName);
    }

    [Fact]
    public async Task OpenPersonalPdf_unknown_material_returns_404()
    {
        await using var tdb = new TestDb();
        var user = new User { Id = Guid.NewGuid(), Email = "student@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "MathGroup2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Linear Algebra",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = "https://example.edu/algebra",
            CreatedAt = DateTimeOffset.UtcNow
        };
        tdb.Db.Users.Add(user);
        tdb.Db.Groups.Add(group);
        tdb.Db.GroupMembers.Add(new GroupMember
        {
            GroupId = group.Id,
            UserId = user.Id,
            Role = GroupRole.Participant,
            JoinedAt = DateTimeOffset.UtcNow
        });
        tdb.Db.Courses.Add(course);
        user.CurrentGroupId = group.Id;
        await tdb.Db.SaveChangesAsync();

        var controller = CreateController(tdb, user.Id);
        var res = await controller.OpenPersonalMaterialPdf(course.Id, Guid.NewGuid(), CancellationToken.None);
        Assert.IsType<NotFoundObjectResult>(res);
    }

    [Fact]
    public async Task SaveGrading_invalid_coeff_sum_returns_400()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "MathGroup2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Linear Algebra",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = "https://example.edu/algebra",
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
        var req = new SaveCourseGradingRequest(
            "How final grade is computed",
            new[]
            {
                new CourseGradingElementRequest("Tests", 0.3m),
                new CourseGradingElementRequest("Exam", 0.5m)
            });

        var res = await controller.SaveGrading(course.Id, req, CancellationToken.None);
        var bad = Assert.IsType<BadRequestObjectResult>(res);
        Assert.Equal("grading_coefficients_sum_must_equal_1", Assert.IsType<ErrorEnvelope>(bad.Value).Error.Code);
    }

    [Fact]
    public async Task SaveGrading_empty_elements_is_allowed()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "MathGroup2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Linear Algebra",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = "https://example.edu/algebra",
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
        var req = new SaveCourseGradingRequest("Text only", Array.Empty<CourseGradingElementRequest>());
        var res = await controller.SaveGrading(course.Id, req, CancellationToken.None);
        Assert.IsType<NoContentResult>(res);
    }

    [Fact]
    public async Task GetGradingText_returns_saved_text()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "MathGroup2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Linear Algebra",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = "https://example.edu/algebra",
            GradingText = "Final score is weighted sum.",
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
        var res = await controller.GetGradingText(course.Id, CancellationToken.None);
        var ok = Assert.IsType<OkObjectResult>(res);
        var payload = Assert.IsType<CourseGradingTextResponse>(ok.Value);
        Assert.Equal("Final score is weighted sum.", payload.Text);
    }

    [Fact]
    public async Task GetGrading_returns_elements_with_default_count_and_average()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "MathGroup2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Linear Algebra",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = "https://example.edu/algebra",
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
        tdb.Db.CourseGradingElements.AddRange(
            new CourseGradingElement
            {
                Id = Guid.NewGuid(),
                CourseId = course.Id,
                Name = "Tests",
                Coefficient = 0.3m,
                Position = 0,
                CreatedAt = DateTimeOffset.UtcNow
            },
            new CourseGradingElement
            {
                Id = Guid.NewGuid(),
                CourseId = course.Id,
                Name = "Homework",
                Coefficient = 0.2m,
                Position = 1,
                CreatedAt = DateTimeOffset.UtcNow
            });
        owner.CurrentGroupId = group.Id;
        await tdb.Db.SaveChangesAsync();

        var controller = CreateController(tdb, owner.Id);
        var res = await controller.GetGrading(course.Id, CancellationToken.None);
        var ok = Assert.IsType<OkObjectResult>(res);
        var payload = Assert.IsType<CourseGradingResponse>(ok.Value);
        Assert.Equal(2, payload.Elements.Count);
        Assert.Equal("Tests", payload.Elements[0].Name);
        Assert.Equal(1, payload.Elements[0].Count);
        Assert.Equal(0m, payload.Elements[0].AverageScore);
    }

    [Fact]
    public async Task ListPersonalMaterials_includes_is_creator_flag()
    {
        await using var tdb = new TestDb();
        var currentUser = new User { Id = Guid.NewGuid(), Email = "student@edu.hse.ru" };
        var otherUser = new User { Id = Guid.NewGuid(), Email = "other@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "MathGroup2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Linear Algebra",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = "https://example.edu/algebra",
            CreatedAt = DateTimeOffset.UtcNow
        };
        tdb.Db.Users.AddRange(currentUser, otherUser);
        tdb.Db.Groups.Add(group);
        tdb.Db.GroupMembers.AddRange(
            new GroupMember
            {
                GroupId = group.Id,
                UserId = currentUser.Id,
                Role = GroupRole.Participant,
                JoinedAt = DateTimeOffset.UtcNow
            },
            new GroupMember
            {
                GroupId = group.Id,
                UserId = otherUser.Id,
                Role = GroupRole.Participant,
                JoinedAt = DateTimeOffset.UtcNow
            });
        tdb.Db.Courses.Add(course);
        tdb.Db.CoursePersonalMaterials.AddRange(
            new CoursePersonalMaterial
            {
                Id = Guid.NewGuid(),
                CourseId = course.Id,
                Name = "my-solution.pdf",
                AuthorUserId = currentUser.Id,
                AuthorEmail = currentUser.Email,
                StoragePath = "personal/my",
                CreatedAt = DateTimeOffset.UtcNow
            },
            new CoursePersonalMaterial
            {
                Id = Guid.NewGuid(),
                CourseId = course.Id,
                Name = "teammate-solution.pdf",
                AuthorUserId = otherUser.Id,
                AuthorEmail = otherUser.Email,
                StoragePath = "personal/other",
                CreatedAt = DateTimeOffset.UtcNow
            });
        currentUser.CurrentGroupId = group.Id;
        await tdb.Db.SaveChangesAsync();

        var controller = CreateController(tdb, currentUser.Id);
        var res = await controller.ListPersonalMaterials(course.Id, CancellationToken.None);
        var ok = Assert.IsType<OkObjectResult>(res);
        var payload = Assert.IsType<CoursePersonalMaterialListResponse>(ok.Value);
        Assert.Equal(2, payload.Materials.Count);
        Assert.Contains(payload.Materials, m => m.Name == "my-solution.pdf" && m.IsCreator);
        Assert.Contains(payload.Materials, m => m.Name == "teammate-solution.pdf" && !m.IsCreator);
    }
}
