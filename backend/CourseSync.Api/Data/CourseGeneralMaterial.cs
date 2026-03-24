namespace CourseSync.Api.Data;

public sealed class CourseGeneralMaterial
{
    public Guid Id { get; set; }
    public Guid CourseId { get; set; }
    public string Name { get; set; } = null!;
    public Guid AuthorUserId { get; set; }
    public string AuthorEmail { get; set; } = null!;
    public string StoragePath { get; set; } = null!;
    public DateTimeOffset CreatedAt { get; set; }

    public Course Course { get; set; } = null!;
}
