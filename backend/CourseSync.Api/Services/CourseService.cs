using CourseSync.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Services;

public sealed class CourseService
{
    public const int CourseNameMaxLength = 50;

    private const int GeneralInfoMaxLength = 2000;
    private const int UsefulLinksMaxLength = 1000;

    private readonly AppDbContext _db;

    public CourseService(AppDbContext db) => _db = db;

    public static (bool Valid, string? ErrorCode) ValidateCourseName(string? name)
    {
        if (string.IsNullOrWhiteSpace(name))
            return (false, "course_name_required");
        name = name.Trim();
        if (name.Length > CourseNameMaxLength)
            return (false, "course_name_too_long");
        return (true, null);
    }

    public static (bool Valid, string? ErrorCode) ValidateGeneralInfo(string? value)
    {
        if (value is null) return (false, "general_info_required");
        var len = value.Length;
        if (len > GeneralInfoMaxLength) return (false, "general_info_too_long");
        return (true, null);
    }

    public static (bool Valid, string? ErrorCode) ValidateUsefulLinks(string? value)
    {
        if (value is null) return (false, "useful_links_required");
        var len = value.Length;
        if (len > UsefulLinksMaxLength) return (false, "useful_links_too_long");
        return (true, null);
    }

    public async Task<(bool Ok, Guid? CourseId, string? Name, string GeneralInfo, string UsefulLinks, string? ErrorCode)> CreateCourseAsync(
        Guid userId,
        Guid groupId,
        string name,
        string generalInfo,
        string usefulLinks,
        CancellationToken ct)
    {
        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null || member.Role != GroupRole.Owner)
            return (false, null, null, "", "", "forbidden");

        var course = new Course
        {
            Id = Guid.NewGuid(),
            GroupId = groupId,
            Name = name.Trim(),
            GeneralInfo = generalInfo ?? "",
            UsefulLinks = usefulLinks ?? "",
            CreatedAt = DateTimeOffset.UtcNow
        };
        _db.Courses.Add(course);
        await _db.SaveChangesAsync(ct);
        return (true, course.Id, course.Name, course.GeneralInfo, course.UsefulLinks, null);
    }

    public async Task<List<CourseListDto>> GetByGroupIdAsync(Guid groupId, CancellationToken ct)
    {
        var list = await _db.Courses
            .Where(c => c.GroupId == groupId)
            .OrderBy(c => c.CreatedAt)
            .Select(c => new CourseListDto(c.Id, c.Name))
            .ToListAsync(ct);
        return list;
    }

    public sealed record CourseListDto(Guid Id, string Name);

    public sealed record CourseDetailDto(
        Guid Id,
        string Name,
        string GeneralInfo,
        string UsefulLinks);

    public async Task<(bool Ok, CourseDetailDto? Data, string? ErrorCode)> GetCourseByIdAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        CancellationToken ct)
    {
        var isMember = await _db.GroupMembers.AnyAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (!isMember)
            return (false, null, "forbidden");

        var course = await _db.Courses
            .AsNoTracking()
            .FirstOrDefaultAsync(c => c.Id == courseId && c.GroupId == groupId, ct);
        if (course is null)
            return (false, null, "course_not_in_group");

        return (true, new CourseDetailDto(course.Id, course.Name, course.GeneralInfo, course.UsefulLinks), null);
    }

    public async Task<(bool Ok, string? ErrorCode)> UpdateCourseAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        string name,
        string generalInfo,
        string usefulLinks,
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

        course.Name = name.Trim();
        course.GeneralInfo = generalInfo ?? "";
        course.UsefulLinks = usefulLinks ?? "";
        await _db.SaveChangesAsync(ct);
        return (true, null);
    }
}
