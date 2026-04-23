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

public sealed class CourseControllerTests
{
    private const string TestUsefulLinksJson = "[{\"title\":\"Сайт\",\"url\":\"https://example.edu/algebra\"}]";

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
        var user = new User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
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
            Name = "Математика2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Линейная алгебра",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = TestUsefulLinksJson,
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
            Name = "Математика2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Линейная алгебра",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = TestUsefulLinksJson,
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
            Name = "Математика2026",
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
            Name = "Математика2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Линейная алгебра",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = TestUsefulLinksJson,
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
        var user = new User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "Математика2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Линейная алгебра",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = TestUsefulLinksJson,
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
            Name = "Математика2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Линейная алгебра",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = TestUsefulLinksJson,
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
                new CourseGradingElementRequest("Tests", 0.3m, null),
                new CourseGradingElementRequest("Exam", 0.5m, null)
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
            Name = "Математика2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Линейная алгебра",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = TestUsefulLinksJson,
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
            Name = "Математика2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Линейная алгебра",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = TestUsefulLinksJson,
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
            Name = "Математика2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Линейная алгебра",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = TestUsefulLinksJson,
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
        Assert.Equal(0m, payload.AverageGrade);
    }

    [Fact]
    public async Task ListPersonalMaterials_includes_is_creator_flag()
    {
        await using var tdb = new TestDb();
        var currentUser = new User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        var otherUser = new User { Id = Guid.NewGuid(), Email = "other@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "Математика2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Линейная алгебра",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = TestUsefulLinksJson,
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

    [Fact]
    public async Task GetGradingScores_returns_score_array_for_element()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "Математика2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Линейная алгебра",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = TestUsefulLinksJson,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var elementId = Guid.NewGuid();
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
        tdb.Db.CourseGradingElements.Add(new CourseGradingElement
        {
            Id = elementId,
            CourseId = course.Id,
            Name = "Tests",
            Coefficient = 0.3m,
            Position = 0,
            CreatedAt = DateTimeOffset.UtcNow
        });
        tdb.Db.CourseGradingScores.AddRange(
            new CourseGradingScore { Id = Guid.NewGuid(), UserId = owner.Id, CourseGradingElementId = elementId, Number = 1, Score = 0m },
            new CourseGradingScore { Id = Guid.NewGuid(), UserId = owner.Id, CourseGradingElementId = elementId, Number = 2, Score = 0m },
            new CourseGradingScore { Id = Guid.NewGuid(), UserId = owner.Id, CourseGradingElementId = elementId, Number = 3, Score = 0m });
        owner.CurrentGroupId = group.Id;
        await tdb.Db.SaveChangesAsync();

        var controller = CreateController(tdb, owner.Id);
        var res = await controller.GetGradingScores(course.Id, "Tests", CancellationToken.None);
        var ok = Assert.IsType<OkObjectResult>(res);
        var payload = Assert.IsType<CourseGradingScoresResponse>(ok.Value);
        Assert.Equal("Tests", payload.Name);
        Assert.Equal(3, payload.Count);
        Assert.Equal(3, payload.Scores.Count);
        Assert.Equal(0m, payload.Scores[0]);
        Assert.Equal(0m, payload.Scores[1]);
        Assert.Equal(0m, payload.Scores[2]);
    }

    [Fact]
    public async Task UpdateGradingScores_updates_averages_used_by_get_grading()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "Математика2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Линейная алгебра",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = TestUsefulLinksJson,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var elementId = Guid.NewGuid();
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
        tdb.Db.CourseGradingElements.Add(new CourseGradingElement
        {
            Id = elementId,
            CourseId = course.Id,
            Name = "Tests",
            Coefficient = 1m,
            Position = 0,
            CreatedAt = DateTimeOffset.UtcNow
        });
        tdb.Db.CourseGradingScores.AddRange(
            new CourseGradingScore { Id = Guid.NewGuid(), UserId = owner.Id, CourseGradingElementId = elementId, Number = 1, Score = 0m },
            new CourseGradingScore { Id = Guid.NewGuid(), UserId = owner.Id, CourseGradingElementId = elementId, Number = 2, Score = 0m },
            new CourseGradingScore { Id = Guid.NewGuid(), UserId = owner.Id, CourseGradingElementId = elementId, Number = 3, Score = 0m });
        owner.CurrentGroupId = group.Id;
        await tdb.Db.SaveChangesAsync();

        var controller = CreateController(tdb, owner.Id);
        var update = new UpdateCourseGradingScoresRequest(
            "Tests",
            new decimal[] { 10m, 3m, 8m });
        var saveRes = await controller.UpdateGradingScores(course.Id, update, CancellationToken.None);
        Assert.IsType<NoContentResult>(saveRes);

        var gradingRes = await controller.GetGrading(course.Id, CancellationToken.None);
        var ok = Assert.IsType<OkObjectResult>(gradingRes);
        var payload = Assert.IsType<CourseGradingResponse>(ok.Value);
        Assert.Equal(7m, payload.Elements[0].AverageScore);
        Assert.Equal(7m, payload.AverageGrade);
    }

    [Fact]
    public async Task UpdateGradingScores_resizes_slot_count_to_match_array_length()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "Математика2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Линейная алгебра",
            GeneralInfo = "Core linear algebra theory and practice.",
            UsefulLinks = TestUsefulLinksJson,
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
        await controller.SaveGrading(
            course.Id,
            new SaveCourseGradingRequest(
                "",
                new[]
                {
                    new CourseGradingElementRequest("Tests", 1m, null)
                }),
            CancellationToken.None);

        var scoresRes = await controller.UpdateGradingScores(
            course.Id,
            new UpdateCourseGradingScoresRequest("Tests", new decimal[] { 5m, 10m }),
            CancellationToken.None);
        Assert.IsType<NoContentResult>(scoresRes);

        var gradingRes = await controller.GetGrading(course.Id, CancellationToken.None);
        var ok = Assert.IsType<OkObjectResult>(gradingRes);
        var payload = Assert.IsType<CourseGradingResponse>(ok.Value);
        Assert.Equal(2, payload.Elements[0].Count);
        Assert.Equal(7.5m, payload.Elements[0].AverageScore);
        Assert.Equal(7.5m, payload.AverageGrade);
    }

    [Fact]
    public async Task UpdateGradingScores_expands_one_slot_to_four_from_mobile_payload()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "Математика2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Линейная алгебра",
            GeneralInfo = "общая информация",
            UsefulLinks = TestUsefulLinksJson,
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
        await controller.SaveGrading(
            course.Id,
            new SaveCourseGradingRequest("", new[] { new CourseGradingElementRequest("Tests", 0.3m, null), new CourseGradingElementRequest("Exam", 0.7m, null) }),
            CancellationToken.None);

        await controller.UpdateGradingScores(
            course.Id,
            new UpdateCourseGradingScoresRequest("Tests", new decimal[] { 10m, 8m, 0m, 0m }),
            CancellationToken.None);

        var gradingRes = await controller.GetGrading(course.Id, CancellationToken.None);
        var payload = Assert.IsType<CourseGradingResponse>(Assert.IsType<OkObjectResult>(gradingRes).Value);
        var tests = payload.Elements.Single(e => e.Name == "Tests");
        Assert.Equal(4, tests.Count);
        Assert.Equal(4.5m, tests.AverageScore);
        Assert.Equal(0m, payload.Elements.Single(e => e.Name == "Exam").AverageScore);
        Assert.Equal(0.3m * 4.5m + 0.7m * 0m, payload.AverageGrade);
    }

    [Fact]
    public async Task GetGrading_average_grade_is_weighted_by_element_coefficients()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "Математика2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Линейная алгебра",
            GeneralInfo = "общая информация",
            UsefulLinks = TestUsefulLinksJson,
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
        await controller.SaveGrading(
            course.Id,
            new SaveCourseGradingRequest(
                "",
                new[]
                {
                    new CourseGradingElementRequest("Коллоквиум", 0.5m, null),
                    new CourseGradingElementRequest("Финальный экзамен", 0.5m, null)
                }),
            CancellationToken.None);

        await controller.UpdateGradingScores(course.Id, new UpdateCourseGradingScoresRequest("Коллоквиум", new decimal[] { 10m }), CancellationToken.None);
        await controller.UpdateGradingScores(course.Id, new UpdateCourseGradingScoresRequest("Финальный экзамен", new decimal[] { 0m }), CancellationToken.None);

        var gradingRes = await controller.GetGrading(course.Id, CancellationToken.None);
        var payload = Assert.IsType<CourseGradingResponse>(Assert.IsType<OkObjectResult>(gradingRes).Value);
        Assert.Equal(10m, payload.Elements[0].AverageScore);
        Assert.Equal(0m, payload.Elements[1].AverageScore);
        Assert.Equal(5m, payload.AverageGrade);
    }

    [Fact]
    public async Task GetGradingScores_without_name_returns_all_elements_scores_count_matches_grading()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "Математика2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Линейная алгебра",
            GeneralInfo = "общая информация",
            UsefulLinks = TestUsefulLinksJson,
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
        await controller.SaveGrading(
            course.Id,
            new SaveCourseGradingRequest(
                "",
                new[]
                {
                    new CourseGradingElementRequest("Тесты", 0.5m, null),
                    new CourseGradingElementRequest("ДЗ", 0.5m, null)
                }),
            CancellationToken.None);

        var res = await controller.GetGradingScores(course.Id, null, CancellationToken.None);
        var ok = Assert.IsType<OkObjectResult>(res);
        var all = Assert.IsType<CourseGradingAllScoresResponse>(ok.Value);
        Assert.Equal(2, all.Elements.Count);
        Assert.Equal("Тесты", all.Elements[0].Name);
        Assert.Equal(1, all.Elements[0].Count);
        Assert.Single(all.Elements[0].Scores);
        Assert.Equal(0m, all.Elements[0].Scores[0]);
        Assert.Equal("ДЗ", all.Elements[1].Name);
        Assert.Equal(1, all.Elements[1].Count);
        Assert.Single(all.Elements[1].Scores);
        Assert.Equal(0m, all.Elements[1].Scores[0]);

        var gradingRes = await controller.GetGrading(course.Id, CancellationToken.None);
        var gradingOk = Assert.IsType<OkObjectResult>(gradingRes);
        var grading = Assert.IsType<CourseGradingResponse>(gradingOk.Value);
        Assert.Equal(2, grading.Elements.Count);
        for (var i = 0; i < 2; i++)
        {
            Assert.Equal(grading.Elements[i].Name, all.Elements[i].Name);
            Assert.Equal(grading.Elements[i].Count, all.Elements[i].Count);
            Assert.Equal(grading.Elements[i].Count, all.Elements[i].Scores.Count);
        }
    }

    [Fact]
    public async Task Grading_scores_are_stored_per_user_not_shared()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var student = new User { Id = Guid.NewGuid(), Email = "user@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "Математика2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Линейная алгебра",
            GeneralInfo = "общая информация",
            UsefulLinks = TestUsefulLinksJson,
            CreatedAt = DateTimeOffset.UtcNow
        };
        tdb.Db.Users.AddRange(owner, student);
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
                UserId = student.Id,
                Role = GroupRole.Participant,
                JoinedAt = DateTimeOffset.UtcNow
            });
        tdb.Db.Courses.Add(course);
        owner.CurrentGroupId = group.Id;
        student.CurrentGroupId = group.Id;
        await tdb.Db.SaveChangesAsync();

        var ownerCtl = CreateController(tdb, owner.Id);
        await ownerCtl.SaveGrading(
            course.Id,
            new SaveCourseGradingRequest("", new[] { new CourseGradingElementRequest("Tests", 1m, null) }),
            CancellationToken.None);

        var studentCtl = CreateController(tdb, student.Id);
        Assert.IsType<NoContentResult>(
            await studentCtl.UpdateGradingScores(
                course.Id,
                new UpdateCourseGradingScoresRequest("Tests", new decimal[] { 9m }),
                CancellationToken.None));

        var studentGrading = Assert.IsType<CourseGradingResponse>(
            Assert.IsType<OkObjectResult>(await studentCtl.GetGrading(course.Id, CancellationToken.None)).Value);
        Assert.Equal(9m, studentGrading.Elements[0].AverageScore);

        var ownerGrading = Assert.IsType<CourseGradingResponse>(
            Assert.IsType<OkObjectResult>(await ownerCtl.GetGrading(course.Id, CancellationToken.None)).Value);
        Assert.Equal(0m, ownerGrading.Elements[0].AverageScore);
    }

    [Fact]
    public async Task Cumulative_grade_and_grading_elements_list_and_block_on_element()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "Линейная алгебра 2026",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Основной курс",
            GeneralInfo = "",
            UsefulLinks = "",
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

        var ctl = CreateController(tdb, owner.Id);
        await ctl.SaveGrading(
            course.Id,
            new SaveCourseGradingRequest(
                "",
                new[]
                {
                    new CourseGradingElementRequest("Tests", 0.2m, 4m),
                    new CourseGradingElementRequest("KR", 0.3m, 0m),
                    new CourseGradingElementRequest("DZ", 0.5m, null)
                }),
            CancellationToken.None);

        var elRes = await ctl.GradingElements(course.Id, CancellationToken.None);
        var elList = Assert.IsType<CourseGradingElementListResponse>(Assert.IsType<OkObjectResult>(elRes).Value);
        Assert.Equal(3, elList.Elements.Count);
        var elementIds = elList.Elements.Select(e => e.Id).ToList();

        Assert.IsType<NoContentResult>(await ctl.SaveCumulativeGrade(
            course.Id,
            new SaveCourseCumulativeGradeRequest(elementIds, 2m, 8m),
            CancellationToken.None));

        await ctl.UpdateGradingScores(course.Id, new UpdateCourseGradingScoresRequest("Tests", new[] { 8m }), CancellationToken.None);
        await ctl.UpdateGradingScores(course.Id, new UpdateCourseGradingScoresRequest("KR", new[] { 10m }), CancellationToken.None);
        await ctl.UpdateGradingScores(course.Id, new UpdateCourseGradingScoresRequest("DZ", new[] { 6m }), CancellationToken.None);

        var grading = Assert.IsType<CourseGradingResponse>(Assert.IsType<OkObjectResult>(await ctl.GetGrading(course.Id, CancellationToken.None)).Value);
        var testsEl = grading.Elements.Single(e => e.Name == "Tests");
        Assert.Equal(4m, testsEl.Block);
        Assert.False(testsEl.IsBlocked);

        var cumulative = Assert.IsType<CourseCumulativeGradeResponse>(Assert.IsType<OkObjectResult>(await ctl.GetCumulativeGrade(course.Id, CancellationToken.None)).Value);
        Assert.Equal(7.6m, cumulative.Value);
        Assert.False(cumulative.IsBlocked);
        Assert.Equal(2m, cumulative.Block);
        Assert.Equal(8m, cumulative.Automatic);
        Assert.False(cumulative.IsAuto!.Value);
        Assert.Equal(new[] { "Tests", "KR", "DZ" }, cumulative.ElementNames);
    }

    [Fact]
    public async Task GetCalendar_returns_events_for_course_sorted_by_date_ascending()
    {
        await using var tdb = new TestDb();
        var owner = new User { Id = Guid.NewGuid(), Email = "owner@edu.hse.ru" };
        var group = new Group
        {
            Id = Guid.NewGuid(),
            Name = "Группа",
            Code = "abcDef",
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Курс",
            GeneralInfo = "",
            UsefulLinks = "",
            CreatedAt = DateTimeOffset.UtcNow
        };
        var otherCourse = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = group.Id,
            Name = "Другой",
            GeneralInfo = "",
            UsefulLinks = "",
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
        tdb.Db.Courses.AddRange(course, otherCourse);
        owner.CurrentGroupId = group.Id;

        var later = new DateTime(2026, 3, 10, 0, 0, 0, DateTimeKind.Utc);
        var earlier = new DateTime(2026, 3, 1, 0, 0, 0, DateTimeKind.Utc);
        tdb.Db.CalendarEvents.AddRange(
            new CalendarEvent
            {
                Id = Guid.NewGuid(),
                GroupId = group.Id,
                CourseId = course.Id,
                EventType = "ДЗ",
                Name = "Позже",
                Date = later,
                Description = ""
            },
            new CalendarEvent
            {
                Id = Guid.NewGuid(),
                GroupId = group.Id,
                CourseId = course.Id,
                EventType = "Тест",
                Name = "Раньше",
                Date = earlier,
                Description = ""
            },
            new CalendarEvent
            {
                Id = Guid.NewGuid(),
                GroupId = group.Id,
                CourseId = otherCourse.Id,
                EventType = "ДЗ",
                Name = "Чужое",
                Date = earlier,
                Description = ""
            });
        await tdb.Db.SaveChangesAsync();

        var controller = CreateController(tdb, owner.Id);
        var res = await controller.GetCalendar(course.Id, CancellationToken.None);
        var ok = Assert.IsType<OkObjectResult>(res.Result);
        var payload = Assert.IsType<CalendarListResponse>(ok.Value);
        Assert.Equal(2, payload.Events.Count);
        Assert.Equal("Раньше", payload.Events[0].Name);
        Assert.Equal("Позже", payload.Events[1].Name);
        Assert.True(payload.Events[0].Date < payload.Events[1].Date);
    }
}
