using CourseSync.Api;
using CourseSync.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Services;

public sealed class NotificationService
{
    private const int FieldGroupNameMinLength = 1;
    private const int FieldGroupNameMaxLength = 50;
    private const int FieldSectionMinLength = 1;
    private const int FieldSectionMaxLength = 50;
    private const int NotificationBodyMaxLength = 3000;

    private readonly AppDbContext _db;

    public NotificationService(AppDbContext db) => _db = db;

    public Task CreateNewsAndPushAsync(
        string type,
        Guid actorUserId,
        Guid groupId,
        string groupName,
        string section,
        string detail,
        CancellationToken ct)
    {
        return CreateNewsAndPushToRecipientsAsync(
            type,
            groupId,
            groupName,
            section,
            detail,
            async token =>
            {
                return await _db.GroupMembers
                    .Where(m => m.GroupId == groupId && m.Role != GroupRole.Owner && m.UserId != actorUserId)
                    .Select(m => m.UserId)
                    .ToListAsync(token);
            },
            markAsReadForUserId: actorUserId,
            ct);
    }

    public Task CreateNewsAndPushToAllMembersExceptAsync(
        string type,
        Guid groupId,
        string groupName,
        string section,
        string detail,
        Guid exceptUserId,
        CancellationToken ct)
    {
        return CreateNewsAndPushToRecipientsAsync(
            type,
            groupId,
            groupName,
            section,
            detail,
            async token =>
            {
                return await _db.GroupMembers
                    .Where(m => m.GroupId == groupId && m.UserId != exceptUserId)
                    .Select(m => m.UserId)
                    .ToListAsync(token);
            },
            markAsReadForUserId: exceptUserId,
            ct);
    }

    public Task CreateNewsAndPushToGroupOwnersAsync(
        string type,
        Guid groupId,
        string groupName,
        string section,
        string detail,
        CancellationToken ct,
        Guid? markAsReadForUserId = null)
    {
        return CreateNewsAndPushToRecipientsAsync(
            type,
            groupId,
            groupName,
            section,
            detail,
            async token =>
            {
                return await _db.GroupMembers
                    .Where(m => m.GroupId == groupId && m.Role == GroupRole.Owner)
                    .Select(m => m.UserId)
                    .ToListAsync(token);
            },
            markAsReadForUserId,
            ct);
    }

    private async Task CreateNewsAndPushToRecipientsAsync(
        string type,
        Guid groupId,
        string groupName,
        string section,
        string detail,
        Func<CancellationToken, Task<List<Guid>>> resolveRecipients,
        Guid? markAsReadForUserId,
        CancellationToken ct)
    {
        var now = DateTimeOffset.UtcNow;
        var groupNameValue = (groupName ?? "").Trim();
        var sectionValue = (section ?? "").Trim();
        var detailValue = detail ?? "";

        if (groupNameValue.Length < FieldGroupNameMinLength || groupNameValue.Length > FieldGroupNameMaxLength)
            throw new ArgumentException(
                $"News group_name length must be between {FieldGroupNameMinLength} and {FieldGroupNameMaxLength}.",
                nameof(groupName));

        if (sectionValue.Length < FieldSectionMinLength || sectionValue.Length > FieldSectionMaxLength)
            throw new ArgumentException(
                $"News section length must be between {FieldSectionMinLength} and {FieldSectionMaxLength}.",
                nameof(section));

        var group = await _db.Groups.AsNoTracking().FirstOrDefaultAsync(g => g.Id == groupId, ct);
        if (group is null)
            throw new InvalidOperationException($"Group {groupId} not found.");

        var news = new News
        {
            Id = Guid.NewGuid(),
            GroupId = groupId,
            GroupName = groupNameValue,
            Section = sectionValue,
            Detail = detailValue,
            Type = type,
            CreatedAt = now
        };
        _db.News.Add(news);

        if (markAsReadForUserId is { } readUid)
        {
            _db.NewsReads.Add(new NewsRead
            {
                UserId = readUid,
                NewsId = news.Id,
                ReadAt = now
            });
        }

        if (detailValue.Length > NotificationBodyMaxLength)
            throw new ArgumentException($"News detail length must be <= {NotificationBodyMaxLength}.", nameof(detail));

        var pushTitle = groupNameValue.Length <= 50 ? groupNameValue : groupNameValue[..49] + "…";
        var pushBody = detailValue.Length > 0
            ? $"{sectionValue}\n\n{detailValue}"
            : sectionValue;

        var memberUserIds = await resolveRecipients(ct);

        foreach (var userId in memberUserIds)
        {
            _db.Notifications.Add(new Notification
            {
                Id = Guid.NewGuid(),
                UserId = userId,
                GroupId = groupId,
                Type = type,
                Title = pushTitle,
                Body = pushBody,
                CreatedAt = now
            });
        }

        await _db.SaveChangesAsync(ct);
        await PruneExcessNewsForGroupAsync(groupId, ct);
    }

    private async Task PruneExcessNewsForGroupAsync(Guid groupId, CancellationToken ct)
    {
        var max = NewsLimits.MaxPerGroup;
        var total = await _db.News.CountAsync(n => n.GroupId == groupId, ct);
        if (total <= max)
            return;

        var toRemove = total - max;
        var oldestRows = await _db.News
            .Where(n => n.GroupId == groupId)
            .OrderBy(n => n.CreatedAt)
            .ThenBy(n => n.Id)
            .Take(toRemove)
            .ToListAsync(ct);

        if (oldestRows.Count == 0)
            return;

        _db.News.RemoveRange(oldestRows);
        await _db.SaveChangesAsync(ct);
    }
}
