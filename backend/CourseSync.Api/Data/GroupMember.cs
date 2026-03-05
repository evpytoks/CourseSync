namespace CourseSync.Api.Data;

public enum GroupRole
{
    Owner = 0,
    Participant = 1
}

public sealed class GroupMember
{
    public Guid GroupId { get; set; }
    public Guid UserId { get; set; }
    public GroupRole Role { get; set; }
    public DateTimeOffset JoinedAt { get; set; }

    public Group Group { get; set; } = null!;
    public User User { get; set; } = null!;
}
