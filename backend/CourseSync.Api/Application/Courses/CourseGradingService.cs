using CourseSync.Api.Data;
using CourseSync.Api.Infrastructure;
using CourseSync.Api.Services;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Application.Courses;

public sealed class CourseGradingService : ICourseGradingService
{
    private readonly AppDbContext _db;
    private readonly NotificationService _notifications;

    public CourseGradingService(AppDbContext db, NotificationService notifications)
    {
        _db = db;
        _notifications = notifications;
    }

    public async Task<(bool Ok, string? ErrorCode)> SaveGradingAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        string? text,
        IReadOnlyList<(string Name, decimal Coefficient, decimal Block)> elements,
        CancellationToken ct)
    {
        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null || member.Role != GroupRole.Owner)
            return (false, "forbidden");

        var textValidation = CourseGradingRules.ValidateGradingText(text);
        if (!textValidation.Valid)
            return (false, textValidation.ErrorCode);

        var elementsValidation = CourseGradingRules.ValidateGradingElements(
            elements.Select(e => (e.Name, e.Coefficient)).ToList());
        if (!elementsValidation.Valid)
            return (false, elementsValidation.ErrorCode);

        foreach (var e in elements)
        {
            if (e.Block < CourseGradingRules.GradingScoreMin || e.Block > CourseGradingRules.GradingScoreMax)
                return (false, "grading_element_block_out_of_range");
        }

        var course = await _db.Courses.FirstOrDefaultAsync(c => c.Id == courseId && c.GroupId == groupId, ct);
        if (course is null)
            return (false, "course_not_in_group");

        var oldGradingText = course.GradingText ?? "";
        var oldElementsRows = await _db.CourseGradingElements
            .AsNoTracking()
            .Where(x => x.CourseId == courseId)
            .OrderBy(x => x.Position)
            .Select(x => new { x.Name, x.Coefficient })
            .ToListAsync(ct);
        var oldFormula = oldElementsRows.Select(x => (x.Name, x.Coefficient)).ToList();

        course.GradingText = text ?? "";

        var old = await _db.CourseGradingElements.Where(x => x.CourseId == courseId).ToListAsync(ct);
        if (old.Count > 0)
            _db.CourseGradingElements.RemoveRange(old);

        var now = DateTimeOffset.UtcNow;
        var position = 0;
        foreach (var element in elements)
        {
            _db.CourseGradingElements.Add(new CourseGradingElement
            {
                Id = Guid.NewGuid(),
                CourseId = courseId,
                Name = element.Name.Trim(),
                Coefficient = decimal.Round(element.Coefficient, 4, MidpointRounding.AwayFromZero),
                Block = decimal.Round(element.Block, 2, MidpointRounding.AwayFromZero),
                Position = position++,
                CreatedAt = now
            });
        }

        await _db.SaveChangesAsync(ct);

        var newFormula = elements.Select(e => (e.Name.Trim(), e.Coefficient)).ToList();
        var oldSnap = NewsFormatting.FormatGradingFormulaLines(oldFormula, oldGradingText);
        var newSnap = NewsFormatting.FormatGradingFormulaLines(newFormula, text ?? "");
        if (oldSnap != newSnap)
        {
            var groupNameG = await CourseGroupDisplayName.GetAsync(_db, groupId, ct);
            await _notifications.CreateNewsAndPushAsync(
                "course_grading_saved",
                userId,
                groupId,
                groupNameG,
                NewsFormatting.SectionCourses,
                NewsFormatting.DetailGradingChangedInGroup(course.Name),
                ct);
        }

        return (true, null);
    }

    public async Task<(bool Ok, string? Text, string? ErrorCode)> GetGradingTextAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        CancellationToken ct)
    {
        var isMember = await _db.GroupMembers.AnyAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (!isMember)
            return (false, null, "forbidden");

        var course = await _db.Courses.AsNoTracking()
            .FirstOrDefaultAsync(c => c.Id == courseId && c.GroupId == groupId, ct);
        if (course is null)
            return (false, null, "course_not_in_group");

        return (true, course.GradingText ?? "", null);
    }

    public async Task<(bool Ok, IReadOnlyList<GradingElementDto>? Elements, string? ErrorCode)> GetGradingAsync(
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

        var elements = await _db.CourseGradingElements
            .AsNoTracking()
            .Where(x => x.CourseId == courseId)
            .OrderBy(x => x.Position)
            .Select(x => new { x.Id, x.Name, x.Coefficient, x.Block })
            .ToListAsync(ct);

        var elementIds = elements.Select(e => e.Id).ToList();
        var scoreRows = await _db.CourseGradingScores
            .AsNoTracking()
            .Where(s => s.UserId == userId && elementIds.Contains(s.CourseGradingElementId))
            .Select(s => new { s.CourseGradingElementId, s.Score })
            .ToListAsync(ct);

        var grouped = scoreRows.GroupBy(x => x.CourseGradingElementId)
            .ToDictionary(g => g.Key, g => g.Select(x => x.Score).ToList());

        var dto = elements
            .Select(e =>
            {
                var rows = grouped.TryGetValue(e.Id, out var list) ? list : new List<decimal>();
                var n = rows.Count;
                var avg = n == 0 ? 0m : decimal.Round(rows.Average(x => x), 2, MidpointRounding.AwayFromZero);
                var blocked = e.Block > 0m && avg < e.Block;
                return new GradingElementDto(
                    e.Name,
                    e.Coefficient,
                    e.Block,
                    DisplaySlotCount(n),
                    avg,
                    blocked);
            })
            .ToList();

        return (true, dto, null);
    }

    public async Task<(bool Ok, string Name, int Count, IReadOnlyList<decimal>? Scores, string? ErrorCode)> GetGradingScoresAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        string elementName,
        CancellationToken ct)
    {
        var isMember = await _db.GroupMembers.AnyAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (!isMember)
            return (false, "", 0, null, "forbidden");

        var element = await _db.CourseGradingElements
            .AsNoTracking()
            .FirstOrDefaultAsync(x => x.CourseId == courseId && x.Name == elementName, ct);
        if (element is null)
            return (false, "", 0, null, "grading_element_not_found");

        var scores = await _db.CourseGradingScores
            .AsNoTracking()
            .Where(s => s.UserId == userId && s.CourseGradingElementId == element.Id)
            .OrderBy(s => s.Number)
            .Select(s => new { s.Number, s.Score })
            .ToListAsync(ct);
        var n = scores.Count;
        var displayCount = DisplaySlotCount(n);
        var normalized = NormalizeScoresToCount(displayCount, scores.Select(x => (x.Number, x.Score)));
        return (true, element.Name, displayCount, normalized, null);
    }

    public async Task<(bool Ok, IReadOnlyList<GradingElementScoresRow>? Elements, string? ErrorCode)> GetAllGradingScoresAsync(
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

        var elements = await _db.CourseGradingElements
            .AsNoTracking()
            .Where(x => x.CourseId == courseId)
            .OrderBy(x => x.Position)
            .Select(x => new { x.Id, x.Name })
            .ToListAsync(ct);

        var elementIds = elements.Select(e => e.Id).ToList();
        var allScores = await _db.CourseGradingScores
            .AsNoTracking()
            .Where(s => s.UserId == userId && elementIds.Contains(s.CourseGradingElementId))
            .Select(s => new { s.CourseGradingElementId, s.Number, s.Score })
            .ToListAsync(ct);

        var byElement = allScores
            .GroupBy(s => s.CourseGradingElementId)
            .ToDictionary(
                g => g.Key,
                g => g.Select(x => (x.Number, x.Score)).ToList());

        var rows = new List<GradingElementScoresRow>(elements.Count);
        foreach (var e in elements)
        {
            var raw = byElement.TryGetValue(e.Id, out var list)
                ? (IReadOnlyList<(int Number, decimal Score)>)list
                : Array.Empty<(int Number, decimal Score)>();
            var n = raw.Count;
            var displayCount = DisplaySlotCount(n);
            rows.Add(new GradingElementScoresRow(e.Name, displayCount, NormalizeScoresToCount(displayCount, raw)));
        }

        return (true, rows, null);
    }

    public async Task<(bool Ok, string? ErrorCode)> UpdateGradingScoresAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        string elementName,
        IReadOnlyList<decimal> scores,
        CancellationToken ct)
    {
        var isMember = await _db.GroupMembers
            .AnyAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (!isMember)
            return (false, "forbidden");

        var element = await _db.CourseGradingElements
            .FirstOrDefaultAsync(x => x.CourseId == courseId && x.Name == elementName, ct);
        if (element is null)
            return (false, "grading_element_not_found");

        if (scores.Count < CourseGradingRules.GradingElementCountMin || scores.Count > CourseGradingRules.GradingElementCountMax)
            return (false, "grading_element_count_invalid");

        foreach (var s in scores)
        {
            if (s < CourseGradingRules.GradingScoreMin || s > CourseGradingRules.GradingScoreMax)
                return (false, "grading_score_out_of_range");
        }

        await ResizeUserGradingScoreRowsAsync(userId, element.Id, scores.Count, ct);
        await _db.SaveChangesAsync(ct);

        var entities = await _db.CourseGradingScores
            .Where(s => s.UserId == userId && s.CourseGradingElementId == element.Id)
            .OrderBy(s => s.Number)
            .ToListAsync(ct);
        if (entities.Count != scores.Count)
            return (false, "grading_scores_count_mismatch");

        for (var i = 0; i < scores.Count; i++)
            entities[i].Score = scores[i];

        await _db.SaveChangesAsync(ct);
        return (true, null);
    }

    public async Task<(bool Ok, IReadOnlyList<(Guid Id, string Name)>? Items, string? ErrorCode)> GetGradingElementOptionsAsync(
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

        var rows = await _db.CourseGradingElements
            .AsNoTracking()
            .Where(x => x.CourseId == courseId)
            .OrderBy(x => x.Position)
            .Select(x => new { x.Id, x.Name })
            .ToListAsync(ct);

        return (true, rows.Select(x => (x.Id, x.Name)).ToList(), null);
    }

    private static int DisplaySlotCount(int userRowCount) =>
        userRowCount == 0 ? 1 : userRowCount;

    private static IReadOnlyList<decimal> NormalizeScoresToCount(int count, IEnumerable<(int Number, decimal Score)> fromDb)
    {
        var map = fromDb
            .GroupBy(s => s.Number)
            .ToDictionary(g => g.Key, g => g.First().Score);
        var list = new List<decimal>(count);
        for (var i = 1; i <= count; i++)
            list.Add(map.TryGetValue(i, out var sc) ? sc : 0m);
        return list;
    }

    private async Task ResizeUserGradingScoreRowsAsync(
        Guid userId,
        Guid courseGradingElementId,
        int newCount,
        CancellationToken ct)
    {
        var existing = await _db.CourseGradingScores
            .Where(s => s.UserId == userId && s.CourseGradingElementId == courseGradingElementId)
            .OrderBy(s => s.Number)
            .ToListAsync(ct);
        var oldCount = existing.Count;

        if (newCount == oldCount)
            return;

        if (newCount < oldCount)
        {
            var toRemove = existing.Where(s => s.Number > newCount).ToList();
            _db.CourseGradingScores.RemoveRange(toRemove);
        }
        else
        {
            for (var i = oldCount + 1; i <= newCount; i++)
            {
                _db.CourseGradingScores.Add(new CourseGradingScore
                {
                    Id = Guid.NewGuid(),
                    UserId = userId,
                    CourseGradingElementId = courseGradingElementId,
                    Number = i,
                    Score = 0m
                });
            }
        }
    }
}
