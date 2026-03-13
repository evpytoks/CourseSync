namespace CourseSync.Api.Data;

public enum DevicePlatform
{
    Unknown = 0,
    Ios = 1,
    Android = 2
}

public sealed class UserDevice
{
    public Guid Id { get; set; }
    public Guid UserId { get; set; }
    public DevicePlatform Platform { get; set; }
    public string Token { get; set; } = "";
    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset? LastUsedAt { get; set; }
    public bool IsActive { get; set; }
}

