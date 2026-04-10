namespace CourseSync.Api.Data;

public sealed class News
{
    public Guid Id { get; set; }
    public Guid GroupId { get; set; }

    public DateTimeOffset CreatedAt { get; set; }
    public string GroupName { get; set; } = "";
    public string Section { get; set; } = "";
    public string Detail { get; set; } = "";

    public string Type { get; set; } = "";

    public Guid? ActorUserId { get; set; }
}
