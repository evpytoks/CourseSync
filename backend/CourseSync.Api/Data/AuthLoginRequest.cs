namespace CourseSync.Api.Data;

public sealed class AuthLoginRequest
{
    public string RequestId { get; set; } = null!;

    public Guid UserId { get; set; }
    public User User { get; set; } = null!;

    public string Email { get; set; } = null!;

    public byte[] CodeHash { get; set; } = null!;

    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset ExpiresAt { get; set; }

    public int AttemptCount { get; set; }
    public DateTimeOffset? UsedAt { get; set; }

    public uint xmin { get; set; }
}
