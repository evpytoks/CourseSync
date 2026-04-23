using CourseSync.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Application.Courses;

internal static class CourseGroupDisplayName
{
    public static async Task<string> GetAsync(AppDbContext db, Guid groupId, CancellationToken ct)
    {
        var name = await db.Groups.AsNoTracking()
            .Where(g => g.Id == groupId)
            .Select(g => g.Name)
            .FirstOrDefaultAsync(ct);
        return string.IsNullOrWhiteSpace(name) ? "Группа" : name.Trim();
    }
}
