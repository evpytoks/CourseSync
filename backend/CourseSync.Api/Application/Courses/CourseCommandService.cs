using CourseSync.Api.Data;
using CourseSync.Api.Infrastructure;
using CourseSync.Api.Infrastructure.Storage;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Application.Courses;

public sealed class CourseCommandService : ICourseCommandService
{
    private readonly AppDbContext _db;
    private readonly NotificationService _notifications;
    private readonly ICourseMaterialBlobStorage _materialBlobs;

    public CourseCommandService(AppDbContext db, NotificationService notifications, ICourseMaterialBlobStorage materialBlobs)
    {
        _db = db;
        _notifications = notifications;
        _materialBlobs = materialBlobs;
    }

    public async Task<(bool Ok, Guid? CourseId, string? Name, string GeneralInfo, IReadOnlyList<CourseContactPersonItem> Contacts, IReadOnlyList<CourseUsefulLinkItem> UsefulLinks, string? ErrorCode)> CreateCourseAsync(
        Guid userId,
        Guid groupId,
        string name,
        string generalInfo,
        IReadOnlyList<CourseContactPersonItem> contacts,
        IReadOnlyList<CourseUsefulLinkItem> usefulLinks,
        CancellationToken ct)
    {
        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null || member.Role != GroupRole.Owner)
            return (false, null, null, "", Array.Empty<CourseContactPersonItem>(), Array.Empty<CourseUsefulLinkItem>(), "forbidden");

        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = groupId,
            Name = name.Trim(),
            GeneralInfo = generalInfo ?? "",
            Contacts = ContactsCodec.ToStorage(contacts),
            UsefulLinks = UsefulLinksCodec.ToStorage(CourseInputRules.NormalizeUsefulLinks(usefulLinks)),
            CreatedAt = DateTimeOffset.UtcNow
        };
        _db.Courses.Add(course);
        await _db.SaveChangesAsync(ct);

        var groupName = await CourseGroupDisplayName.GetAsync(_db, groupId, ct);
        await _notifications.CreateNewsAndPushAsync(
            "course_created",
            userId,
            groupId,
            groupName,
            NewsFormatting.SectionCourses,
            NewsFormatting.DetailCourseCreatedInGroup(course.Name),
            ct);

        return (true, course.Id, course.Name, course.GeneralInfo, ContactsCodec.FromStorage(course.Contacts), CourseInputRules.NormalizeUsefulLinks(UsefulLinksCodec.FromStorage(course.UsefulLinks)), null);
    }

    public async Task<(bool Ok, string? ErrorCode)> UpdateCourseAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        string name,
        string generalInfo,
        IReadOnlyList<CourseContactPersonItem> contacts,
        IReadOnlyList<CourseUsefulLinkItem> usefulLinks,
        CancellationToken ct)
    {
        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null)
            return (false, "forbidden");

        var course = await _db.Courses
            .FirstOrDefaultAsync(c => c.Id == courseId && c.GroupId == groupId, ct);
        if (course is null)
            return (false, "course_not_in_group");

        if (member.Role != GroupRole.Owner)
            return (false, "forbidden");

        var oldName = course.Name;
        var oldGeneral = course.GeneralInfo ?? "";
        var oldContacts = course.Contacts ?? "";
        var oldLinks = course.UsefulLinks ?? "";
        course.Name = name.Trim();
        course.GeneralInfo = generalInfo ?? "";
        course.Contacts = ContactsCodec.ToStorage(contacts);
        course.UsefulLinks = UsefulLinksCodec.ToStorage(CourseInputRules.NormalizeUsefulLinks(usefulLinks));

        var nameChanged = TrimField(oldName) != TrimField(course.Name);
        var restChanged = NewsFormatting.BuildChangedCourseFieldsGeneralLinks(
            oldGeneral,
            course.GeneralInfo,
            oldContacts,
            course.Contacts,
            oldLinks,
            course.UsefulLinks);

        await _db.SaveChangesAsync(ct);

        if (nameChanged || restChanged is not null)
        {
            var groupName = await CourseGroupDisplayName.GetAsync(_db, groupId, ct);
            string detail;
            if (nameChanged && restChanged is null)
                detail = NewsFormatting.DetailCourseRenamedInGroup(oldName, course.Name);
            else if (nameChanged && restChanged is not null)
                detail = NewsFormatting.DetailCourseRenamedInGroup(oldName, course.Name) + "\n\n" + restChanged;
            else
                detail = NewsFormatting.DetailCourseFieldsUpdatedInGroup(course.Name, restChanged!);

            await _notifications.CreateNewsAndPushAsync(
                "course_updated",
                userId,
                groupId,
                groupName,
                NewsFormatting.SectionCourses,
                detail,
                ct);
        }

        return (true, null);
    }

    public async Task<(bool Ok, string? ErrorCode)> DeleteCourseAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        CancellationToken ct)
    {
        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null)
            return (false, "forbidden");

        var course = await _db.Courses
            .FirstOrDefaultAsync(c => c.Id == courseId && c.GroupId == groupId, ct);
        if (course is null)
            return (false, "course_not_in_group");

        if (member.Role != GroupRole.Owner)
            return (false, "forbidden");

        var genPaths = await _db.CourseGeneralMaterials
            .Where(m => m.CourseId == courseId)
            .Select(m => m.StoragePath)
            .ToListAsync(ct);
        var perPaths = await _db.CoursePersonalMaterials
            .Where(m => m.CourseId == courseId)
            .Select(m => m.StoragePath)
            .ToListAsync(ct);
        foreach (var path in genPaths.Concat(perPaths).Distinct())
        {
            try
            {
                await _materialBlobs.DeleteAsync(path, ct);
            }
            catch
            {
            }
        }

        var courseName = course.Name;
        _db.Courses.Remove(course);
        await _db.SaveChangesAsync(ct);

        var groupNameDel = await CourseGroupDisplayName.GetAsync(_db, groupId, ct);
        await _notifications.CreateNewsAndPushAsync(
            "course_deleted",
            userId,
            groupId,
            groupNameDel,
            NewsFormatting.SectionCourses,
            NewsFormatting.DetailCourseDeleted(courseName),
            ct);

        return (true, null);
    }

    private static string TrimField(string s) => (s ?? "").Trim();
}
