using CourseSync.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Services;

public sealed class CalendarService
{
    private const int DescriptionMaxLength = 2000;

    private readonly AppDbContext _db;
    private readonly NotificationService _notifications;

    public CalendarService(AppDbContext db, NotificationService notifications)
    {
        _db = db;
        _notifications = notifications;
    }

    public static (bool Valid, string? ErrorCode) ValidateEventName(string? name)
    {
        if (string.IsNullOrWhiteSpace(name))
            return (false, "calendar_name_required");
        name = name.Trim();
        if (name.Length < 1)
            return (false, "calendar_name_too_short");
        if (name.Length > 20)
            return (false, "calendar_name_too_long");
        return (true, null);
    }

    public static (bool Valid, string? ErrorCode) ValidateDescription(string? description)
    {
        if (description is null)
            return (true, null);

        var len = description.Length;
        if (len > DescriptionMaxLength)
            return (false, "calendar_description_too_long");

        return (true, null);
    }

    public static (bool Valid, string? ErrorCode) ValidateDateRange(DateOnly startDate, DateOnly endDate)
    {
        if (endDate < startDate)
            return (false, "calendar_date_range_invalid");
        return (true, null);
    }

    internal static string FormatCalendarEventDescription(string name, DateTime date, string description)
    {
        var text = $"Название: {name}\nДата: {date:yyyy-MM-dd HH:mm}\nОписание: {description}";
        return text.Length > 2000 ? text[..2000] : text;
    }

    public async Task<(bool Ok, List<CalendarEventListDto>? Events, string? ErrorCode)> GetEventsAsync(
        Guid userId,
        Guid groupId,
        DateOnly startDate,
        DateOnly endDate,
        CancellationToken ct)
    {
        var start = new DateTime(startDate.Year, startDate.Month, startDate.Day, 0, 0, 0, DateTimeKind.Utc);
        var endExclusive = new DateTime(endDate.Year, endDate.Month, endDate.Day, 0, 0, 0, DateTimeKind.Utc).AddDays(1);

        var isMember = await _db.GroupMembers.AnyAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (!isMember)
            return (false, null, "forbidden");

        var list = await _db.CalendarEvents
            .Where(e => e.GroupId == groupId && e.Date >= start && e.Date < endExclusive)
            .OrderBy(e => e.Date)
            .Select(e => new CalendarEventListDto(e.Id, e.Name, e.Date))
            .ToListAsync(ct);

        return (true, list, null);
    }

    public async Task<(bool Ok, Guid? Id, string? ErrorCode)> CreateEventAsync(
        Guid userId,
        Guid groupId,
        string name,
        DateTime date,
        string description,
        CancellationToken ct)
    {
        date = DateTime.SpecifyKind(date, DateTimeKind.Utc);

        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null || member.Role != GroupRole.Owner)
            return (false, null, "forbidden");

        var entity = new CalendarEvent
        {
            Id = Guid.NewGuid(),
            GroupId = groupId,
            Name = name.Trim(),
            Date = date,
            Description = description ?? ""
        };
        _db.CalendarEvents.Add(entity);
        await _db.SaveChangesAsync(ct);

        await _notifications.CreateNewsAndPushAsync(
            "calendar_event_created",
            userId,
            groupId,
            "Календарь: новое событие",
            FormatCalendarEventDescription(entity.Name, entity.Date, entity.Description ?? ""),
            ct);

        return (true, entity.Id, null);
    }

    public async Task<(bool Ok, CalendarEventDetailsDto? Event, string? ErrorCode)> GetEventAsync(
        Guid userId,
        Guid groupId,
        Guid eventId,
        CancellationToken ct)
    {
        var entity = await _db.CalendarEvents.FirstOrDefaultAsync(e => e.Id == eventId, ct);
        if (entity is null)
            return (false, null, "calendar_event_not_found");

        if (entity.GroupId != groupId)
            return (false, null, "forbidden");

        var isMember = await _db.GroupMembers.AnyAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (!isMember)
            return (false, null, "forbidden");

        var dto = new CalendarEventDetailsDto(entity.Id, entity.Name, entity.Date, entity.Description);
        return (true, dto, null);
    }

    public async Task<(bool Ok, string? ErrorCode)> UpdateEventAsync(
        Guid userId,
        Guid groupId,
        Guid eventId,
        string name,
        DateTime date,
        string description,
        CancellationToken ct)
    {
        var entity = await _db.CalendarEvents.FirstOrDefaultAsync(e => e.Id == eventId, ct);
        if (entity is null)
            return (false, "calendar_event_not_found");

        if (entity.GroupId != groupId)
            return (false, "forbidden");

        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null || member.Role != GroupRole.Owner)
            return (false, "forbidden");

        date = DateTime.SpecifyKind(date, DateTimeKind.Utc);

        entity.Name = name.Trim();
        entity.Date = date;
        entity.Description = description ?? "";

        await _db.SaveChangesAsync(ct);

        await _notifications.CreateNewsAndPushAsync(
            "calendar_event_updated",
            userId,
            groupId,
            "Календарь: изменение события",
            FormatCalendarEventDescription(entity.Name, entity.Date, entity.Description ?? ""),
            ct);

        return (true, null);
    }

    public async Task<(bool Ok, string? ErrorCode)> DeleteEventAsync(
        Guid userId,
        Guid groupId,
        Guid eventId,
        CancellationToken ct)
    {
        var entity = await _db.CalendarEvents.FirstOrDefaultAsync(e => e.Id == eventId, ct);
        if (entity is null)
            return (false, "calendar_event_not_found");

        if (entity.GroupId != groupId)
            return (false, "forbidden");

        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null || member.Role != GroupRole.Owner)
            return (false, "forbidden");

        _db.CalendarEvents.Remove(entity);
        await _db.SaveChangesAsync(ct);

        await _notifications.CreateNewsAndPushAsync(
            "calendar_event_deleted",
            userId,
            groupId,
            "Календарь: удаление события",
            FormatCalendarEventDescription(entity.Name, entity.Date, entity.Description ?? ""),
            ct);

        return (true, null);
    }

    public sealed record CalendarEventListDto(Guid Id, string Name, DateTime Date);

    public sealed record CalendarEventDetailsDto(Guid Id, string Name, DateTime Date, string Description);
}

