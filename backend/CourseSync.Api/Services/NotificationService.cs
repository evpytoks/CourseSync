using CourseSync.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Services;

public sealed class NotificationService
{
    private const int NotificationTitleMinLength = 1;
    private const int NotificationTitleMaxLength = 50;
    private const int NotificationBodyMaxLength = 3000;

    private readonly AppDbContext _db;

    public NotificationService(AppDbContext db) => _db = db;

    public Task CreateNewsAndPushAsync(
        string type,
        Guid actorUserId,
        Guid groupId,
        string title,
        string description,
        CancellationToken ct)
    {
        return CreateNewsAndPushToRecipientsAsync(
            type,
            groupId,
            title,
            description,
            async token =>
            {
                return await _db.GroupMembers
                    .Where(m => m.GroupId == groupId && m.Role != GroupRole.Owner && m.UserId != actorUserId)
                    .Select(m => m.UserId)
                    .ToListAsync(token);
            },
            ct);
    }

    public Task CreateNewsAndPushToAllMembersExceptAsync(
        string type,
        Guid groupId,
        string title,
        string description,
        Guid exceptUserId,
        CancellationToken ct)
    {
        return CreateNewsAndPushToRecipientsAsync(
            type,
            groupId,
            title,
            description,
            async token =>
            {
                return await _db.GroupMembers
                    .Where(m => m.GroupId == groupId && m.UserId != exceptUserId)
                    .Select(m => m.UserId)
                    .ToListAsync(token);
            },
            ct);
    }

    private async Task CreateNewsAndPushToRecipientsAsync(
        string type,
        Guid groupId,
        string title,
        string description,
        Func<CancellationToken, Task<List<Guid>>> resolveRecipients,
        CancellationToken ct)
    {
        var now = DateTimeOffset.UtcNow;
        var titleValue = (title ?? "").Trim();
        var descriptionValue = description ?? "";

        if (titleValue.Length < NotificationTitleMinLength || titleValue.Length > NotificationTitleMaxLength)
            throw new ArgumentException(
                $"Notification title length must be between {NotificationTitleMinLength} and {NotificationTitleMaxLength}.",
                nameof(title));

        var news = new News
        {
            Id = Guid.NewGuid(),
            GroupId = groupId,
            Title = titleValue,
            Description = descriptionValue,
            Type = type,
            CreatedAt = now
        };
        _db.News.Add(news);

        var body = string.IsNullOrEmpty(descriptionValue) ? titleValue : descriptionValue;
        if (body.Length > NotificationBodyMaxLength)
            throw new ArgumentException($"Notification body length must be <= {NotificationBodyMaxLength}.", nameof(description));

        var memberUserIds = await resolveRecipients(ct);

        foreach (var userId in memberUserIds)
        {
            _db.Notifications.Add(new Notification
            {
                Id = Guid.NewGuid(),
                UserId = userId,
                GroupId = groupId,
                Type = type,
                Title = titleValue,
                Body = body,
                CreatedAt = now
            });
        }

        await _db.SaveChangesAsync(ct);
    }
}
