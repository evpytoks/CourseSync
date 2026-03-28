using CourseSync.Api.Data;
using CourseSync.Api.Infrastructure.Storage;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Services;

public sealed class CourseService
{
    public const int CourseNameMaxLength = 50;
    public const int GradingTextMaxLength = 3000;
    public const int GradingElementNameMaxLength = 50;
    public const int GradingElementCountMin = 1;
    public const int GradingElementCountMax = 100;
    public const decimal GradingScoreMin = 0m;
    public const decimal GradingScoreMax = 10m;

    private const int GeneralInfoMaxLength = 2000;
    private const int UsefulLinksMaxLength = 1000;

    private readonly AppDbContext _db;
    private readonly NotificationService _notifications;
    private readonly ICourseMaterialBlobStorage _materialBlobs;

    public CourseService(AppDbContext db, NotificationService notifications, ICourseMaterialBlobStorage materialBlobs)
    {
        _db = db;
        _notifications = notifications;
        _materialBlobs = materialBlobs;
    }

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

        await _notifications.CreateNewsAndPushAsync(
            "course_created",
            userId,
            groupId,
            "Новый курс",
            $"Добавлен курс: {course.Name}",
            ct);

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

    public sealed record GradingElementDto(string Name, decimal Coefficient, int Count, decimal AverageScore);
    public sealed record GradingElementScoresRow(string Name, int Count, IReadOnlyList<decimal> Scores);

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

        await _notifications.CreateNewsAndPushAsync(
            "course_updated",
            userId,
            groupId,
            "Курс обновлён",
            $"Курс: {course.Name}",
            ct);

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

        await _notifications.CreateNewsAndPushAsync(
            "course_deleted",
            userId,
            groupId,
            "Курс удалён",
            $"Удалён курс: {courseName}",
            ct);

        return (true, null);
    }

    public static (bool Valid, string? ErrorCode) ValidateGradingText(string? text)
    {
        if ((text ?? "").Length > GradingTextMaxLength)
            return (false, "grading_text_too_long");
        return (true, null);
    }

    public static (bool Valid, string? ErrorCode) ValidateGradingElements(IReadOnlyList<(string Name, decimal Coefficient, int Count)> elements)
    {
        if (elements.Count == 0)
            return (true, null);

        decimal sum = 0m;
        foreach (var element in elements)
        {
            var name = (element.Name ?? "").Trim();
            if (name.Length == 0)
                return (false, "grading_element_name_required");
            if (name.Length > GradingElementNameMaxLength)
                return (false, "grading_element_name_too_long");
            if (element.Coefficient < 0m || element.Coefficient > 1m)
                return (false, "grading_coefficient_out_of_range");
            if (element.Count < GradingElementCountMin || element.Count > GradingElementCountMax)
                return (false, "grading_element_count_invalid");
            sum += element.Coefficient;
        }

        if (Math.Abs(sum - 1m) > 0.0001m)
            return (false, "grading_coefficients_sum_must_equal_1");

        return (true, null);
    }

    public async Task<(bool Ok, string? ErrorCode)> SaveGradingAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        string? text,
        IReadOnlyList<(string Name, decimal Coefficient, int Count)> elements,
        CancellationToken ct)
    {
        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null || member.Role != GroupRole.Owner)
            return (false, "forbidden");

        var textValidation = ValidateGradingText(text);
        if (!textValidation.Valid)
            return (false, textValidation.ErrorCode);

        var elementsValidation = ValidateGradingElements(elements);
        if (!elementsValidation.Valid)
            return (false, elementsValidation.ErrorCode);

        var course = await _db.Courses.FirstOrDefaultAsync(c => c.Id == courseId && c.GroupId == groupId, ct);
        if (course is null)
            return (false, "course_not_in_group");

        course.GradingText = text ?? "";

        var old = await _db.CourseGradingElements.Where(x => x.CourseId == courseId).ToListAsync(ct);
        if (old.Count > 0)
            _db.CourseGradingElements.RemoveRange(old);

        var now = DateTimeOffset.UtcNow;
        var position = 0;
        foreach (var element in elements)
        {
            var gradingElement = new CourseGradingElement
            {
                Id = Guid.NewGuid(),
                CourseId = courseId,
                Name = element.Name.Trim(),
                Coefficient = decimal.Round(element.Coefficient, 4, MidpointRounding.AwayFromZero),
                Count = element.Count,
                Position = position++,
                CreatedAt = now
            };
            _db.CourseGradingElements.Add(gradingElement);
            for (var i = 1; i <= element.Count; i++)
            {
                _db.CourseGradingScores.Add(new CourseGradingScore
                {
                    Id = Guid.NewGuid(),
                    CourseGradingElementId = gradingElement.Id,
                    Number = i,
                    Score = 0m
                });
            }
        }

        await _db.SaveChangesAsync(ct);
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
            .Select(x => new { x.Id, x.Name, x.Coefficient, x.Count })
            .ToListAsync(ct);

        var elementIds = elements.Select(e => e.Id).ToList();
        var scoreAverages = await _db.CourseGradingScores
            .AsNoTracking()
            .Where(s => elementIds.Contains(s.CourseGradingElementId))
            .GroupBy(s => s.CourseGradingElementId)
            .Select(g => new { ElementId = g.Key, Average = g.Average(x => x.Score) })
            .ToListAsync(ct);
        var avgMap = scoreAverages.ToDictionary(x => x.ElementId, x => decimal.Round(x.Average, 2, MidpointRounding.AwayFromZero));

        var dto = elements
            .Select(e => new GradingElementDto(
                e.Name,
                e.Coefficient,
                e.Count,
                avgMap.TryGetValue(e.Id, out var avg) ? avg : 0m))
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
            .Where(s => s.CourseGradingElementId == element.Id)
            .OrderBy(s => s.Number)
            .Select(s => new { s.Number, s.Score })
            .ToListAsync(ct);
        var normalized = NormalizeScoresToCount(element.Count, scores.Select(x => (x.Number, x.Score)));
        return (true, element.Name, element.Count, normalized, null);
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
            .Select(x => new { x.Id, x.Name, x.Count })
            .ToListAsync(ct);

        var elementIds = elements.Select(e => e.Id).ToList();
        var allScores = await _db.CourseGradingScores
            .AsNoTracking()
            .Where(s => elementIds.Contains(s.CourseGradingElementId))
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
            rows.Add(new GradingElementScoresRow(e.Name, e.Count, NormalizeScoresToCount(e.Count, raw)));
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
        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null || member.Role != GroupRole.Owner)
            return (false, "forbidden");

        var element = await _db.CourseGradingElements
            .FirstOrDefaultAsync(x => x.CourseId == courseId && x.Name == elementName, ct);
        if (element is null)
            return (false, "grading_element_not_found");

        if (scores.Count < GradingElementCountMin || scores.Count > GradingElementCountMax)
            return (false, "grading_element_count_invalid");

        foreach (var s in scores)
        {
            if (s < GradingScoreMin || s > GradingScoreMax)
                return (false, "grading_score_out_of_range");
        }

        await ResizeGradingElementScoreRowsAsync(element, scores.Count, ct);
        await _db.SaveChangesAsync(ct);

        var entities = await _db.CourseGradingScores
            .Where(s => s.CourseGradingElementId == element.Id)
            .OrderBy(s => s.Number)
            .ToListAsync(ct);
        if (entities.Count != scores.Count)
            return (false, "grading_scores_count_mismatch");

        for (var i = 0; i < scores.Count; i++)
            entities[i].Score = scores[i];

        await _db.SaveChangesAsync(ct);
        return (true, null);
    }

    private async Task ResizeGradingElementScoreRowsAsync(CourseGradingElement element, int newCount, CancellationToken ct)
    {
        if (newCount == element.Count)
            return;

        if (newCount < element.Count)
        {
            var toRemove = await _db.CourseGradingScores
                .Where(s => s.CourseGradingElementId == element.Id && s.Number > newCount)
                .ToListAsync(ct);
            _db.CourseGradingScores.RemoveRange(toRemove);
        }
        else
        {
            for (var i = element.Count + 1; i <= newCount; i++)
            {
                _db.CourseGradingScores.Add(new CourseGradingScore
                {
                    Id = Guid.NewGuid(),
                    CourseGradingElementId = element.Id,
                    Number = i,
                    Score = 0m
                });
            }
        }

        element.Count = newCount;
    }
}
