namespace CourseSync.Api.Data;

public sealed class User
{
    public Guid Id { get; set; }
    public string Email { get; set; } = null!;
    public int TokenVersion { get; set; }
}
