using System.Text.RegularExpressions;
using CourseSync.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Services;

public sealed class CourseService
{
    private static readonly Regex CourseNameRegex = new(@"^[a-zA-Zа-яА-ЯёЁ0-9]{1,20}$", RegexOptions.Compiled);
    private const int GeneralInfoMaxLength = 2000;
    private const int UsefulLinksMaxLength = 2000;

    private readonly AppDbContext _db;

    public CourseService(AppDbContext db) => _db = db;

    public static (bool Valid, string? ErrorCode) ValidateCourseName(string? name)
    {
        if (string.IsNullOrWhiteSpace(name))
            return (false, "course_name_required");
        name = name.Trim();
        if (name.Length > 20)
            return (false, "course_name_too_long");
        if (!CourseNameRegex.IsMatch(name))
            return (false, "course_name_invalid");
        return (true, null);
    }

    public static (bool Valid, string? ErrorCode) ValidateGeneralInfo(string? value)
    {
        if (value is null) return (true, null);
        var len = value.Length;
        if (len == 0 || string.IsNullOrWhiteSpace(value)) return (false, "general_info_too_short");
        if (len > GeneralInfoMaxLength) return (false, "general_info_too_long");
        return (true, null);
    }

    public static (bool Valid, string? ErrorCode) ValidateUsefulLinks(string? value)
    {
        if (value is null) return (true, null);
        var len = value.Length;
        if (len == 0 || string.IsNullOrWhiteSpace(value)) return (false, "useful_links_too_short");
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
}
