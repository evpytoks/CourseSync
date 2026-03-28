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
                Count = 1,
                Position = 0,
                CreatedAt = DateTimeOffset.UtcNow
            },
            new CourseGradingElement
            {
                Id = Guid.NewGuid(),
                CourseId = course.Id,
                Name = "Homework",
                Coefficient = 0.2m,
                Count = 1,
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

    [Fact]
    public async Task GetGradingScores_returns_score_array_for_element()
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
            Count = 3,
            Position = 0,
            CreatedAt = DateTimeOffset.UtcNow
        });
        tdb.Db.CourseGradingScores.AddRange(
            new CourseGradingScore { Id = Guid.NewGuid(), CourseGradingElementId = elementId, Number = 1, Score = 0m },
            new CourseGradingScore { Id = Guid.NewGuid(), CourseGradingElementId = elementId, Number = 2, Score = 0m },
            new CourseGradingScore { Id = Guid.NewGuid(), CourseGradingElementId = elementId, Number = 3, Score = 0m });
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
            Count = 3,
            Position = 0,
            CreatedAt = DateTimeOffset.UtcNow
        });
        tdb.Db.CourseGradingScores.AddRange(
            new CourseGradingScore { Id = Guid.NewGuid(), CourseGradingElementId = elementId, Number = 1, Score = 0m },
            new CourseGradingScore { Id = Guid.NewGuid(), CourseGradingElementId = elementId, Number = 2, Score = 0m },
            new CourseGradingScore { Id = Guid.NewGuid(), CourseGradingElementId = elementId, Number = 3, Score = 0m });
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
        await controller.SaveGrading(
            course.Id,
            new SaveCourseGradingRequest(
                "",
                new[]
                {
                    new CourseGradingElementRequest("Tests", 1m)
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
        await controller.SaveGrading(
            course.Id,
            new SaveCourseGradingRequest("", new[] { new CourseGradingElementRequest("Tests", 0.3m), new CourseGradingElementRequest("Exam", 0.7m) }),
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
        await controller.SaveGrading(
            course.Id,
            new SaveCourseGradingRequest(
                "",
                new[]
                {
                    new CourseGradingElementRequest("A", 0.5m),
                    new CourseGradingElementRequest("B", 0.5m)
                }),
            CancellationToken.None);

        await controller.UpdateGradingScores(course.Id, new UpdateCourseGradingScoresRequest("A", new decimal[] { 10m }), CancellationToken.None);
        await controller.UpdateGradingScores(course.Id, new UpdateCourseGradingScoresRequest("B", new decimal[] { 0m }), CancellationToken.None);

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
        await controller.SaveGrading(
            course.Id,
            new SaveCourseGradingRequest(
                "",
                new[]
                {
                    new CourseGradingElementRequest("Тесты", 0.5m),
                    new CourseGradingElementRequest("ДЗ", 0.5m)
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
}
