namespace CourseSync.Api.Data;

public sealed class GroupMemberBlock
{
    public Guid GroupId { get; set; }
    public Guid UserId { get; set; }
    public DateTimeOffset BlockedAt { get; set; }

    public Group Group { get; set; } = null!;
    public User User { get; set; } = null!;
}
