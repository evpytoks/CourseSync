namespace CourseSync.Api.Data;

public sealed class Course
{
    public Guid Id { get; set; }
    public Guid GroupId { get; set; }
    public string Name { get; set; } = null!;
    public string GeneralInfo { get; set; } = "";
    public string UsefulLinks { get; set; } = "";
    public DateTimeOffset CreatedAt { get; set; }
}
