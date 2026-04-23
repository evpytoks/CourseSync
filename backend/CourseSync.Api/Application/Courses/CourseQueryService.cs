using CourseSync.Api.Data;
using CourseSync.Api.Infrastructure;
using CourseSync.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Application.Courses;

public sealed class CourseQueryService : ICourseQueryService
{
    private readonly AppDbContext _db;

    public CourseQueryService(AppDbContext db) => _db = db;

    public async Task<List<CourseListDto>> GetByGroupIdAsync(Guid groupId, CancellationToken ct)
    {
        var list = await _db.Courses
            .Where(c => c.GroupId == groupId)
            .OrderBy(c => c.CreatedAt)
            .Select(c => new CourseListDto(c.Id, c.Name))
            .ToListAsync(ct);
        return list;
    }

    public async Task<(bool Ok, List<CourseListDto>? Courses, string? ErrorCode)> GetByGroupIdForOwnerAsync(
        Guid userId,
        Guid groupId,
        CancellationToken ct)
    {
        var isOwner = await _db.GroupMembers
            .AnyAsync(m => m.UserId == userId && m.GroupId == groupId && m.Role == GroupRole.Owner, ct);
        if (!isOwner)
            return (false, null, "forbidden");

        var courses = await GetByGroupIdAsync(groupId, ct);
        return (true, courses, null);
    }

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

        return (true, new CourseDetailDto(
            course.Id,
            course.Name,
            course.GeneralInfo,
            ContactsCodec.FromStorage(course.Contacts),
            CourseInputRules.NormalizeUsefulLinks(UsefulLinksCodec.FromStorage(course.UsefulLinks))), null);
    }
}
