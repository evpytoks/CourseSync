namespace CourseSync.Api.Data;

public sealed class CalendarEventUserState
{
    public Guid EventId { get; set; }
    public Guid UserId { get; set; }
    public bool IsDone { get; set; }
}
