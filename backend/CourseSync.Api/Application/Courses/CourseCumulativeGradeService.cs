using CourseSync.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Application.Courses;

public sealed class CourseCumulativeGradeService : ICourseCumulativeGradeService
{
    private readonly AppDbContext _db;

    public CourseCumulativeGradeService(AppDbContext db) => _db = db;

    public static (bool Valid, string? ErrorCode) ValidateOptionalCumulativeGradeThreshold(decimal? value)
    {
        if (value is null)
            return (true, null);
        if (value.Value < CourseGradingRules.GradingScoreMin || value.Value > CourseGradingRules.GradingScoreMax)
            return (false, "cumulative_grade_threshold_out_of_range");
        return (true, null);
    }

    public async Task<(bool Ok, string? ErrorCode)> SaveCumulativeGradeAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        IReadOnlyList<Guid>? elementIds,
        decimal? block,
        decimal? automatic,
        CancellationToken ct)
    {
        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null || member.Role != GroupRole.Owner)
            return (false, "forbidden");

        var course = await _db.Courses.AsNoTracking()
            .FirstOrDefaultAsync(c => c.Id == courseId && c.GroupId == groupId, ct);
        if (course is null)
            return (false, "course_not_in_group");

        if (elementIds is null || elementIds.Count == 0)
            return (false, "cumulative_grade_elements_required");

        var orderedIds = new List<Guid>(elementIds.Count);
        var seen = new HashSet<Guid>();
        foreach (var id in elementIds)
        {
            if (id == Guid.Empty)
                return (false, "cumulative_grade_element_id_invalid");
            if (!seen.Add(id))
                return (false, "cumulative_grade_duplicate_element");
            orderedIds.Add(id);
        }

        var rows = await _db.CourseGradingElements.AsNoTracking()
            .Where(x => x.CourseId == courseId && orderedIds.Contains(x.Id))
            .Select(x => x.Id)
            .ToListAsync(ct);
        var rowSet = rows.ToHashSet();
        foreach (var id in orderedIds)
        {
            if (!rowSet.Contains(id))
                return (false, "cumulative_grade_element_not_found");
        }

        var bOk = ValidateOptionalCumulativeGradeThreshold(block);
        if (!bOk.Valid)
            return (false, bOk.ErrorCode);
        var aOk = ValidateOptionalCumulativeGradeThreshold(automatic);
        if (!aOk.Valid)
            return (false, aOk.ErrorCode);

        if (block is { } b && automatic is { } a && b > a)
            return (false, "cumulative_grade_block_greater_than_automatic");

        var blockToStore = !block.HasValue || block.Value == 0m ? 0m : block.Value;

        var existingRows = await _db.CourseCumulativeGradeElements
            .Where(x => x.CourseCumulativeGradeId == courseId)
            .ToListAsync(ct);
        if (existingRows.Count > 0)
            _db.CourseCumulativeGradeElements.RemoveRange(existingRows);

        var cumulative = await _db.CourseCumulativeGrades.FirstOrDefaultAsync(x => x.CourseId == courseId, ct);
        var now = DateTimeOffset.UtcNow;
        if (cumulative is null)
        {
            cumulative = new CourseCumulativeGrade { CourseId = courseId };
            _db.CourseCumulativeGrades.Add(cumulative);
        }

        cumulative.Block = blockToStore;
        cumulative.AutomaticThreshold = automatic;
        cumulative.UpdatedAt = now;

        for (var i = 0; i < orderedIds.Count; i++)
        {
            _db.CourseCumulativeGradeElements.Add(new CourseCumulativeGradeElement
            {
                Id = Guid.NewGuid(),
                CourseCumulativeGradeId = courseId,
                CourseGradingElementId = orderedIds[i],
                Position = i
            });
        }

        await _db.SaveChangesAsync(ct);
        return (true, null);
    }

    public async Task<(bool Ok, CourseCumulativeGradeDetailDto? Data, string? ErrorCode)> GetCumulativeGradeAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        CancellationToken ct)
    {
        var isMember = await _db.GroupMembers.AnyAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (!isMember)
            return (false, null, "forbidden");

        var courseOk = await _db.Courses.AnyAsync(c => c.Id == courseId && c.GroupId == groupId, ct);
        if (!courseOk)
            return (false, null, "course_not_in_group");

        var cumulative = await _db.CourseCumulativeGrades.AsNoTracking()
            .FirstOrDefaultAsync(x => x.CourseId == courseId, ct);
        if (cumulative is null)
            return (false, null, "cumulative_grade_not_configured");

        var orderedElementIds = await _db.CourseCumulativeGradeElements
            .AsNoTracking()
            .Where(x => x.CourseCumulativeGradeId == courseId)
            .OrderBy(x => x.Position)
            .Select(x => x.CourseGradingElementId)
            .ToListAsync(ct);

        if (orderedElementIds.Count == 0)
            return (false, null, "cumulative_grade_not_configured");

        var gradingRows = await _db.CourseGradingElements
            .AsNoTracking()
            .Where(x => x.CourseId == courseId && orderedElementIds.Contains(x.Id))
            .Select(x => new { x.Id, x.Name, x.Coefficient })
            .ToListAsync(ct);
        var byId = gradingRows.ToDictionary(x => x.Id);

        var coeffs = new List<decimal>(orderedElementIds.Count);
        var elementNames = new List<string>(orderedElementIds.Count);
        foreach (var geId in orderedElementIds)
        {
            if (!byId.TryGetValue(geId, out var row))
                return (false, null, "cumulative_grade_configuration_stale");
            coeffs.Add(row.Coefficient);
            elementNames.Add(row.Name);
        }

        var elementIds = orderedElementIds;
        var scoreRows = await _db.CourseGradingScores
            .AsNoTracking()
            .Where(s => s.UserId == userId && elementIds.Contains(s.CourseGradingElementId))
            .Select(s => new { s.CourseGradingElementId, s.Score })
            .ToListAsync(ct);

        var grouped = scoreRows.GroupBy(x => x.CourseGradingElementId)
            .ToDictionary(g => g.Key, g => g.Select(x => x.Score).ToList());

        decimal sum = 0m;
        for (var i = 0; i < coeffs.Count; i++)
        {
            var geId = orderedElementIds[i];
            var listRows = grouped.TryGetValue(geId, out var list) ? list : new List<decimal>();
            var n = listRows.Count;
            var avg = n == 0 ? 0m : decimal.Round(listRows.Average(x => x), 2, MidpointRounding.AwayFromZero);
            sum += decimal.Round(coeffs[i] * avg, 4, MidpointRounding.AwayFromZero);
        }

        var value = decimal.Round(sum, 2, MidpointRounding.AwayFromZero);
        var blockOut = cumulative.Block ?? 0m;
        var isBlocked = blockOut > 0m && value < blockOut;
        bool? isAuto = cumulative.AutomaticThreshold is { } auto
            ? value >= auto
            : null;

        return (true, new CourseCumulativeGradeDetailDto(value, blockOut, cumulative.AutomaticThreshold, isBlocked, isAuto, elementNames), null);
    }
}
