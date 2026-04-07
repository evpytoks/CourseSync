namespace CourseSync.Api.Data;

public sealed class CalendarEvent
{
    public Guid Id { get; set; }
    public Guid GroupId { get; set; }
    public Guid? CourseId { get; set; }
    public string EventType { get; set; } = "";
    public string Name { get; set; } = "";
    public DateTime Date { get; set; }
    public string Description { get; set; } = "";
}

