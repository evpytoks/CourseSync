namespace CourseSync.Api.Data;

public sealed class News
{
    public Guid Id { get; set; }
    public Guid GroupId { get; set; }
    public string Title { get; set; } = "";
    public string Description { get; set; } = "";
    public string Type { get; set; } = "";
    public DateTimeOffset CreatedAt { get; set; }
}
