using CourseSync.Api.Data;
using CourseSync.Api.Infrastructure;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Services;

public sealed class NewsService
{
    private const int NewsTextMaxLength = 3000;

    private readonly AppDbContext _db;
    private readonly NotificationService _notificationService;

    public NewsService(AppDbContext db, NotificationService notificationService)
    {
        _db = db;
        _notificationService = notificationService;
    }

    public async Task<List<NewsListDto>> GetAllForUserAsync(Guid userId, CancellationToken ct)
    {
        return await _db.News
            .AsNoTracking()
            .Where(n => _db.GroupMembers.Any(m => m.UserId == userId && m.GroupId == n.GroupId))
            .OrderByDescending(n => n.CreatedAt)
            .Select(n => new NewsListDto(n.Id, n.CreatedAt, n.GroupName, n.Section, n.Detail))
            .ToListAsync(ct);
    }

    public async Task<(bool Ok, NewsDetailsDto? Item, string? ErrorCode)> GetByIdAsync(
        Guid userId,
        Guid newsId,
        CancellationToken ct)
    {
        var news = await _db.News
            .AsNoTracking()
            .FirstOrDefaultAsync(n => n.Id == newsId, ct);
        if (news is null)
            return (false, null, "news_not_found");

        var isMember = await _db.GroupMembers.AnyAsync(
            m => m.GroupId == news.GroupId && m.UserId == userId, ct);
        if (!isMember)
            return (false, null, "forbidden");

        return (true, new NewsDetailsDto(news.Id, news.CreatedAt, news.GroupName, news.Section, news.Detail), null);
    }

    public static (bool Valid, string? ErrorCode) ValidateNewsText(string? text)
    {
        if (string.IsNullOrWhiteSpace(text))
            return (false, "news_text_required");
        if (text.Trim().Length > NewsTextMaxLength)
            return (false, "news_text_too_long");
        return (true, null);
    }

    public async Task<(bool Ok, string? ErrorCode)> CheckOwnerAndCreateNewsAsync(
        Guid userId,
        Guid groupId,
        string text,
        CancellationToken ct)
    {
        var isOwner = await _db.GroupMembers.AnyAsync(
            m => m.GroupId == groupId && m.UserId == userId && m.Role == GroupRole.Owner,
            ct);
        if (!isOwner)
            return (false, "forbidden");

        var group = await _db.Groups.AsNoTracking().FirstOrDefaultAsync(g => g.Id == groupId, ct);
        var groupName = string.IsNullOrWhiteSpace(group?.Name) ? "Группа" : group!.Name.Trim();

        await _notificationService.CreateNewsAndPushAsync(
            "manual",
            userId,
            groupId,
            groupName,
            NewsFormatting.SectionNews,
            text.Trim(),
            ct);
        return (true, null);
    }

    public sealed record NewsListDto(Guid Id, DateTimeOffset Time, string Group, string Section, string Text);
    public sealed record NewsDetailsDto(Guid Id, DateTimeOffset Time, string Group, string Section, string Text);
}
