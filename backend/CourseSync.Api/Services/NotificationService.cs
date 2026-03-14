using CourseSync.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Services;

public sealed class NotificationService
{
    private readonly AppDbContext _db;

    public NotificationService(AppDbContext db) => _db = db;

    public async Task CreateNewsAndPushAsync(
        string type,
        Guid actorUserId,
        Guid groupId,
        string title,
        string description,
        CancellationToken ct)
    {
        var now = DateTimeOffset.UtcNow;
        var titleTrimmed = title.Length > 20 ? title[..20] : title;
        var descriptionTrimmed = description.Length > 2000 ? description[..2000] : description;

        var news = new News
        {
            Id = Guid.NewGuid(),
            GroupId = groupId,
            Title = titleTrimmed,
            Description = descriptionTrimmed,
            Type = type,
            CreatedAt = now
        };
        _db.News.Add(news);

        var memberUserIds = await _db.GroupMembers
            .Where(m => m.GroupId == groupId && m.Role != GroupRole.Owner)
            .Select(m => m.UserId)
            .ToListAsync(ct);

        var body = string.IsNullOrEmpty(descriptionTrimmed) ? titleTrimmed : descriptionTrimmed;
        if (body.Length > 2000) body = body[..2000];

        foreach (var userId in memberUserIds)
        {
            if (userId == actorUserId)
                continue;

            _db.Notifications.Add(new Notification
            {
                Id = Guid.NewGuid(),
                UserId = userId,
                GroupId = groupId,
                Type = type,
                Title = titleTrimmed.Length > 200 ? titleTrimmed[..200] : titleTrimmed,
                Body = body,
                CreatedAt = now
            });
        }

        await _db.SaveChangesAsync(ct);
    }
}

