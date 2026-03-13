using CourseSync.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Services;

public sealed class NotificationService
{
    private readonly AppDbContext _db;

    public NotificationService(AppDbContext db) => _db = db;

    public async Task CreateCalendarEventNotificationForGroupAsync(
        string type,
        Guid actorUserId,
        Guid groupId,
        CalendarEvent calendarEvent,
        CancellationToken ct)
    {
        var memberUserIds = await _db.GroupMembers
            .Where(m => m.GroupId == groupId && m.Role != GroupRole.Owner)
            .Select(m => m.UserId)
            .ToListAsync(ct);

        if (memberUserIds.Count == 0)
            return;

        var now = DateTimeOffset.UtcNow;
        var title = calendarEvent.Name;
        var body = $"Event \"{calendarEvent.Name}\" on {calendarEvent.Date:O}";

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
                Title = title,
                Body = body,
                CreatedAt = now
            });
        }

        await _db.SaveChangesAsync(ct);
    }
}

