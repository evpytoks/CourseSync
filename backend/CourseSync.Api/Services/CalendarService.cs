using CourseSync.Api.Data;
using CourseSync.Api.Infrastructure;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Services;

public sealed class CalendarService : ICalendarService
{
    private const int NameMaxLength = 50;
    private const int DescriptionMaxLength = 1000;
    private readonly AppDbContext _db;
    private readonly NotificationService _notifications;

    public CalendarService(AppDbContext db, NotificationService notifications)
    {
        _db = db;
        _notifications = notifications;
    }

    public static (bool Valid, string? ErrorCode) ValidateEventName(string? name)
    {
        var trimmed = (name ?? "").Trim();
        if (trimmed.Length > NameMaxLength)
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

    public static (bool Valid, string? ErrorCode) ValidateEventType(string? eventType)
    {
        if (!CalendarEventCatalog.TryNormalizeType(eventType, out _))
            return (false, "calendar_event_type_invalid");
        return (true, null);
    }

    public async Task<(bool Ok, List<CalendarEventListDto>? Events, string? ErrorCode)> GetEventsAsync(
        Guid userId,
        DateOnly? startDate,
        DateOnly? endDate,
        CancellationToken ct)
    {
        var groupIds = await _db.GroupMembers
            .Where(m => m.UserId == userId)
            .Select(m => m.GroupId)
            .ToListAsync(ct);
        if (groupIds.Count == 0)
            return (true, new List<CalendarEventListDto>(), null);

        var typeColors = await GetTypeColorsAsync(userId, ct);
        var colorsByType = typeColors.ToDictionary(x => x.Type, x => x.Color, StringComparer.Ordinal);

        var statesForUser = _db.CalendarEventUserStates.Where(s => s.UserId == userId);

        IQueryable<CalendarEvent> eventsQuery = _db.CalendarEvents.Where(e => groupIds.Contains(e.GroupId));
        if (startDate is not null && endDate is not null)
        {
            var start = new DateTime(startDate.Value.Year, startDate.Value.Month, startDate.Value.Day, 0, 0, 0, DateTimeKind.Utc);
            var endExclusive = new DateTime(endDate.Value.Year, endDate.Value.Month, endDate.Value.Day, 0, 0, 0, DateTimeKind.Utc).AddDays(1);
            eventsQuery = eventsQuery.Where(e => e.Date >= start && e.Date < endExclusive);
        }

        var list = await (
            from e in eventsQuery
            join g in _db.Groups on e.GroupId equals g.Id
            join c in _db.Courses on e.CourseId equals c.Id into courseJoin
            from c in courseJoin.DefaultIfEmpty()
            join s in statesForUser on e.Id equals s.EventId into stateJoin
            from s in stateJoin.DefaultIfEmpty()
            orderby e.Date
            select new CalendarEventListDto(
                e.Id,
                e.GroupId,
                g.Name,
                e.CourseId,
                c != null ? c.Name : null,
                e.EventType,
                e.Name,
                e.Date,
                s != null && s.IsDone))
            .ToListAsync(ct);

        foreach (var item in list)
            item.EventColor = colorsByType.TryGetValue(item.EventType, out var color) ? color : CalendarEventCatalog.DefaultColor;

        return (true, list, null);
    }

    public async Task<(bool Ok, List<CalendarEventListDto>? Events, string? ErrorCode)> GetEventsForCourseAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        CancellationToken ct)
    {
        var isMember = await _db.GroupMembers.AnyAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (!isMember)
            return (false, null, "forbidden");

        var courseInGroup = await _db.Courses.AnyAsync(c => c.Id == courseId && c.GroupId == groupId, ct);
        if (!courseInGroup)
            return (false, null, "course_not_in_group");

        var typeColors = await GetTypeColorsAsync(userId, ct);
        var colorsByType = typeColors.ToDictionary(x => x.Type, x => x.Color, StringComparer.Ordinal);

        var statesForUser = _db.CalendarEventUserStates.Where(s => s.UserId == userId);

        var list = await (
            from e in _db.CalendarEvents
            where e.GroupId == groupId && e.CourseId == courseId
            join g in _db.Groups on e.GroupId equals g.Id
            join c in _db.Courses on e.CourseId equals c.Id into courseJoin
            from c in courseJoin.DefaultIfEmpty()
            join s in statesForUser on e.Id equals s.EventId into stateJoin
            from s in stateJoin.DefaultIfEmpty()
            orderby e.Date
            select new CalendarEventListDto(
                e.Id,
                e.GroupId,
                g.Name,
                e.CourseId,
                c != null ? c.Name : null,
                e.EventType,
                e.Name,
                e.Date,
                s != null && s.IsDone))
            .ToListAsync(ct);

        foreach (var item in list)
            item.EventColor = colorsByType.TryGetValue(item.EventType, out var color) ? color : CalendarEventCatalog.DefaultColor;

        return (true, list, null);
    }

    public async Task<(bool Ok, Guid? Id, string? ErrorCode)> CreateEventAsync(
        Guid userId,
        Guid groupId,
        Guid? courseId,
        string eventType,
        string? name,
        DateTime date,
        string? description,
        CancellationToken ct)
    {
        date = DateTime.SpecifyKind(date, DateTimeKind.Utc);

        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null || member.Role != GroupRole.Owner)
            return (false, null, "forbidden");

        if (!CalendarEventCatalog.TryNormalizeType(eventType, out var normalizedType))
            return (false, null, "calendar_event_type_invalid");

        if (courseId is not null)
        {
            var hasCourse = await _db.Courses.AnyAsync(c => c.Id == courseId.Value && c.GroupId == groupId, ct);
            if (!hasCourse)
                return (false, null, "calendar_course_not_in_group");
        }

        var entity = new CalendarEvent
        {
            Id = Guid.NewGuid(),
            GroupId = groupId,
            CourseId = courseId,
            EventType = normalizedType,
            Name = (name ?? "").Trim(),
            Date = date,
            Description = description ?? ""
        };
        _db.CalendarEvents.Add(entity);
        await _db.SaveChangesAsync(ct);

        var groupName = await GetGroupNameAsync(groupId, ct);
        await _notifications.CreateNewsAndPushAsync(
            "calendar_event_created",
            userId,
            groupId,
            groupName,
            NewsFormatting.SectionCalendar,
            NewsFormatting.DetailCalendarEventCreated(
                entity.Name,
                entity.Date,
                entity.Description ?? ""),
            ct);

        return (true, entity.Id, null);
    }

    public async Task<(bool Ok, CalendarEventDetailsDto? Event, string? ErrorCode)> GetEventAsync(
        Guid userId,
        Guid eventId,
        CancellationToken ct)
    {
        var entity = await _db.CalendarEvents.FirstOrDefaultAsync(e => e.Id == eventId, ct);
        if (entity is null)
            return (false, null, "calendar_event_not_found");

        var isMember = await _db.GroupMembers.AnyAsync(m => m.GroupId == entity.GroupId && m.UserId == userId, ct);
        if (!isMember)
            return (false, null, "forbidden");

        var groupName = await GetGroupNameAsync(entity.GroupId, ct);
        var courseName = entity.CourseId is null
            ? null
            : await _db.Courses
                .Where(c => c.Id == entity.CourseId.Value)
                .Select(c => c.Name)
                .FirstOrDefaultAsync(ct);
        var state = await _db.CalendarEventUserStates
            .Where(s => s.EventId == eventId && s.UserId == userId)
            .Select(s => (bool?)s.IsDone)
            .FirstOrDefaultAsync(ct);
        var colors = await GetTypeColorsAsync(userId, ct);
        var color = colors.FirstOrDefault(x => x.Type == entity.EventType).Color ?? CalendarEventCatalog.DefaultColor;

        var dto = new CalendarEventDetailsDto(
            entity.Id,
            entity.GroupId,
            groupName,
            entity.CourseId,
            courseName,
            entity.EventType,
            color,
            entity.Name,
            entity.Date,
            entity.Description,
            state ?? false);
        return (true, dto, null);
    }

    public async Task<(bool Ok, string? ErrorCode)> UpdateEventAsync(
        Guid userId,
        Guid eventId,
        Guid? courseId,
        string eventType,
        string? name,
        DateTime date,
        string? description,
        CancellationToken ct)
    {
        var entity = await _db.CalendarEvents.FirstOrDefaultAsync(e => e.Id == eventId, ct);
        if (entity is null)
            return (false, "calendar_event_not_found");

        var groupId = entity.GroupId;

        if (!CalendarEventCatalog.TryNormalizeType(eventType, out var normalizedType))
            return (false, "calendar_event_type_invalid");

        if (courseId is not null)
        {
            var hasCourse = await _db.Courses.AnyAsync(c => c.Id == courseId.Value && c.GroupId == groupId, ct);
            if (!hasCourse)
                return (false, "calendar_course_not_in_group");
        }

        if (courseId is null && entity.CourseId is not null)
            entity.CourseId = null;

        if (courseId is not null)
            entity.CourseId = courseId;

        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null || member.Role != GroupRole.Owner)
            return (false, "forbidden");

        date = DateTime.SpecifyKind(date, DateTimeKind.Utc);

        var oldName = entity.Name;
        var oldDate = entity.Date;
        var oldDescription = entity.Description ?? "";
        var oldType = entity.EventType;
        var oldCourseId = entity.CourseId;

        entity.Name = (name ?? "").Trim();
        entity.Date = date;
        entity.Description = description ?? "";
        entity.EventType = normalizedType;

        await _db.SaveChangesAsync(ct);

        var newDescription = entity.Description ?? "";
        if (oldName != entity.Name || oldDate != entity.Date || oldDescription != newDescription || oldType != entity.EventType || oldCourseId != entity.CourseId)
        {
            var groupNameU = await GetGroupNameAsync(groupId, ct);
            await _notifications.CreateNewsAndPushAsync(
                "calendar_event_updated",
                userId,
                groupId,
                groupNameU,
                NewsFormatting.SectionCalendar,
                NewsFormatting.DetailCalendarEventUpdated(
                    entity.Name,
                    entity.Date,
                    newDescription,
                    oldName,
                    oldDate,
                    oldDescription),
                ct);
        }

        return (true, null);
    }

    public async Task<(bool Ok, string? ErrorCode)> DeleteEventAsync(
        Guid userId,
        Guid eventId,
        CancellationToken ct)
    {
        var entity = await _db.CalendarEvents.FirstOrDefaultAsync(e => e.Id == eventId, ct);
        if (entity is null)
            return (false, "calendar_event_not_found");

        var groupId = entity.GroupId;

        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null || member.Role != GroupRole.Owner)
            return (false, "forbidden");

        var delName = entity.Name;
        var delDate = entity.Date;
        var delDescription = entity.Description ?? "";
        _db.CalendarEvents.Remove(entity);
        await _db.SaveChangesAsync(ct);

        var groupNameD = await GetGroupNameAsync(groupId, ct);
        await _notifications.CreateNewsAndPushAsync(
            "calendar_event_deleted",
            userId,
            groupId,
            groupNameD,
            NewsFormatting.SectionCalendar,
            NewsFormatting.DetailCalendarEventDeleted(delName, delDate, delDescription),
            ct);

        return (true, null);
    }

    public async Task<(bool Ok, string? ErrorCode)> ToggleDoneAsync(
        Guid userId,
        Guid eventId,
        CancellationToken ct)
    {
        var entity = await _db.CalendarEvents.FirstOrDefaultAsync(e => e.Id == eventId, ct);
        if (entity is null)
            return (false, "calendar_event_not_found");

        var isMember = await _db.GroupMembers.AnyAsync(m => m.GroupId == entity.GroupId && m.UserId == userId, ct);
        if (!isMember)
            return (false, "forbidden");

        var state = await _db.CalendarEventUserStates
            .FirstOrDefaultAsync(s => s.EventId == eventId && s.UserId == userId, ct);
        var next = !(state?.IsDone ?? false);

        if (state is null)
        {
            _db.CalendarEventUserStates.Add(new CalendarEventUserState
            {
                EventId = eventId,
                UserId = userId,
                IsDone = next
            });
        }
        else
        {
            state.IsDone = next;
        }

        await _db.SaveChangesAsync(ct);
        return (true, null);
    }

    public async Task<IReadOnlyList<(string Type, string Color)>> GetTypeColorsAsync(Guid userId, CancellationToken ct)
    {
        var raw = await _db.Users
            .Where(u => u.Id == userId)
            .Select(u => u.CalendarEventTypeColors)
            .FirstOrDefaultAsync(ct);

        var overrides = CalendarEventTypeColorsCodec.FromStorage(raw);
        return UserService.ResolveCalendarEventTypeColors(overrides);
    }

    public async Task<IReadOnlyList<(string Type, string Color)>> GetTypesForUserAsync(Guid userId, CancellationToken ct)
        => await GetTypeColorsAsync(userId, ct);

    private async Task<string> GetGroupNameAsync(Guid groupId, CancellationToken ct)
    {
        var name = await _db.Groups.AsNoTracking()
            .Where(g => g.Id == groupId)
            .Select(g => g.Name)
            .FirstOrDefaultAsync(ct);
        return string.IsNullOrWhiteSpace(name) ? "Группа" : name.Trim();
    }

    public sealed record CalendarEventListDto(
        Guid Id,
        Guid GroupId,
        string GroupName,
        Guid? CourseId,
        string? CourseName,
        string EventType,
        string Name,
        DateTime Date,
        bool IsDone)
    {
        public string EventColor { get; set; } = CalendarEventCatalog.DefaultColor;
    }

    public sealed record CalendarEventDetailsDto(
        Guid Id,
        Guid GroupId,
        string GroupName,
        Guid? CourseId,
        string? CourseName,
        string EventType,
        string EventColor,
        string Name,
        DateTime Date,
        string Description,
        bool IsDone);
}

