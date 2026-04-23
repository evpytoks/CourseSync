namespace CourseSync.Api.Services;

public interface ICalendarService
{
    Task<(bool Ok, List<CalendarService.CalendarEventListDto>? Events, string? ErrorCode)> GetEventsAsync(
        Guid userId,
        DateOnly? startDate,
        DateOnly? endDate,
        CancellationToken ct);
    Task<(bool Ok, List<CalendarService.CalendarEventListDto>? Events, string? ErrorCode)> GetEventsForCourseAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        CancellationToken ct);
    Task<(bool Ok, Guid? Id, string? ErrorCode)> CreateEventAsync(
        Guid userId,
        Guid groupId,
        Guid? courseId,
        string eventType,
        string? name,
        DateTime date,
        string? description,
        CancellationToken ct);
    Task<(bool Ok, CalendarService.CalendarEventDetailsDto? Event, string? ErrorCode)> GetEventAsync(
        Guid userId,
        Guid eventId,
        CancellationToken ct);
    Task<(bool Ok, string? ErrorCode)> UpdateEventAsync(
        Guid userId,
        Guid eventId,
        Guid? courseId,
        string eventType,
        string? name,
        DateTime date,
        string? description,
        CancellationToken ct);
    Task<(bool Ok, string? ErrorCode)> DeleteEventAsync(Guid userId, Guid eventId, CancellationToken ct);
    Task<(bool Ok, string? ErrorCode)> ToggleDoneAsync(Guid userId, Guid eventId, CancellationToken ct);
    Task<IReadOnlyList<(string Type, string Color)>> GetTypesForUserAsync(Guid userId, CancellationToken ct);
}
