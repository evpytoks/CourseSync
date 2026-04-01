using CourseSync.Api.Data;
using CourseSync.Api.Services;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class CourseServiceTests
{
    private const string TestUsefulLinksJson = "[{\"title\":\"Сайт\",\"url\":\"https://example.edu/algebra\"}]";

    private static CourseService CreateSvc(TestDb tdb) =>
        new(tdb.Db, new NotificationService(tdb.Db), new NoOpCourseMaterialBlobStorage());

    [Fact]
    public async Task DeleteCourseAsync_owner_removes_course_and_material_rows()
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
        tdb.Db.CourseGeneralMaterials.Add(new CourseGeneralMaterial
        {
            Id = Guid.NewGuid(),
            CourseId = course.Id,
            Name = "lecture-notes.pdf",
            AuthorUserId = owner.Id,
            AuthorEmail = owner.Email,
            StoragePath = "path/gen",
            CreatedAt = DateTimeOffset.UtcNow
        });
        await tdb.Db.SaveChangesAsync();

        var svc = CreateSvc(tdb);
        var (ok, err) = await svc.DeleteCourseAsync(owner.Id, group.Id, course.Id, CancellationToken.None);
        Assert.True(ok);
        Assert.Null(err);
        Assert.False(await tdb.Db.Courses.AnyAsync(c => c.Id == course.Id));
        Assert.False(await tdb.Db.CourseGeneralMaterials.AnyAsync(m => m.CourseId == course.Id));
    }

    [Fact]
    public async Task DeleteCourseAsync_participant_forbidden()
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
        await tdb.Db.SaveChangesAsync();

        var svc = CreateSvc(tdb);
        var (ok, err) = await svc.DeleteCourseAsync(participant.Id, group.Id, course.Id, CancellationToken.None);
        Assert.False(ok);
        Assert.Equal("forbidden", err);
        Assert.True(await tdb.Db.Courses.AnyAsync(c => c.Id == course.Id));
    }

    [Fact]
    public async Task DeleteCourseAsync_wrong_course_id_returns_course_not_in_group()
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
        await tdb.Db.SaveChangesAsync();

        var svc = CreateSvc(tdb);
        var (ok, err) = await svc.DeleteCourseAsync(owner.Id, group.Id, Guid.NewGuid(), CancellationToken.None);
        Assert.False(ok);
        Assert.Equal("course_not_in_group", err);
    }
}
