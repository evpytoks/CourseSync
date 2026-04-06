namespace CourseSync.Api.Data;

public sealed class NewsRead
{
    public Guid UserId { get; set; }
    public Guid NewsId { get; set; }
    public DateTimeOffset ReadAt { get; set; }

    public User User { get; set; } = null!;
    public News News { get; set; } = null!;
}
