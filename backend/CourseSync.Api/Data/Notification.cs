namespace CourseSync.Api.Data;

public sealed class Notification
{
    public Guid Id { get; set; }
    public Guid UserId { get; set; }
    public Guid GroupId { get; set; }
    public string Type { get; set; } = "";
    public string Title { get; set; } = "";
    public string Body { get; set; } = "";
    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset? SentAt { get; set; }
    public int SendAttempts { get; set; }
}

