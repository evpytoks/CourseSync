using CourseSync.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Services;

public sealed class NewsService
{
    private readonly AppDbContext _db;
    private readonly NotificationService _notificationService;

    public NewsService(AppDbContext db, NotificationService notificationService)
    {
        _db = db;
        _notificationService = notificationService;
    }

    public async Task<(bool Ok, List<NewsListDto>? Items, string? ErrorCode)> GetByGroupIdAsync(
        Guid userId,
        Guid groupId,
        CancellationToken ct)
    {
        var isMember = await _db.GroupMembers.AnyAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (!isMember)
            return (false, null, "forbidden");

        var list = await _db.News
            .Where(n => n.GroupId == groupId)
            .OrderByDescending(n => n.CreatedAt)
            .Select(n => new NewsListDto(n.Id, n.Title, n.CreatedAt))
            .ToListAsync(ct);

        return (true, list, null);
    }

    public async Task<(bool Ok, NewsDetailsDto? Item, string? ErrorCode)> GetByIdAsync(
        Guid userId,
        Guid groupId,
        Guid newsId,
        CancellationToken ct)
    {
        var isMember = await _db.GroupMembers.AnyAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (!isMember)
            return (false, null, "forbidden");

        var news = await _db.News
            .FirstOrDefaultAsync(n => n.Id == newsId && n.GroupId == groupId, ct);
        if (news is null)
            return (false, null, "news_not_found");

        return (true, new NewsDetailsDto(news.Id, news.Title, news.Description, news.CreatedAt), null);
    }

    public static (bool Valid, string? ErrorCode) ValidateNewsName(string? name)
    {
        if (string.IsNullOrWhiteSpace(name))
            return (false, "news_name_required");
        name = name.Trim();
        if (name.Length < 1)
            return (false, "news_name_too_short");
        if (name.Length > 20)
            return (false, "news_name_too_long");
        return (true, null);
    }

    public static (bool Valid, string? ErrorCode) ValidateNewsDescription(string? description)
    {
        if (description is null)
            return (true, null);
        if (description.Length > 2000)
            return (false, "news_description_too_long");
        return (true, null);
    }

    public async Task<(bool Ok, string? ErrorCode)> CheckOwnerAndCreateNewsAsync(
        Guid userId,
        Guid groupId,
        string title,
        string description,
        CancellationToken ct)
    {
        var isOwner = await _db.GroupMembers.AnyAsync(
            m => m.GroupId == groupId && m.UserId == userId && m.Role == GroupRole.Owner,
            ct);
        if (!isOwner)
            return (false, "forbidden");

        await _notificationService.CreateNewsAndPushAsync(
            "manual",
            userId,
            groupId,
            title,
            description,
            ct);
        return (true, null);
    }

    public sealed record NewsListDto(Guid Id, string Title, DateTimeOffset CreatedAt);
    public sealed record NewsDetailsDto(Guid Id, string Title, string Description, DateTimeOffset CreatedAt);
}
