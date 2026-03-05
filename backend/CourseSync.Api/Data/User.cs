namespace CourseSync.Api.Data;

public sealed class User
{
    public Guid Id { get; set; }
    public string Email { get; set; } = null!;
    public int TokenVersion { get; set; }
    public int AuthCodeFailCount { get; set; }
    public DateTimeOffset? AuthCodeLockoutUntil { get; set; }
    public DateTimeOffset? AuthCodeLastSentAt { get; set; }
    public Guid? CurrentGroupId { get; set; }
}
