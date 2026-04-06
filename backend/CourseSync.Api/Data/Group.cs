namespace CourseSync.Api.Data;

public sealed class Group
{
    public Guid Id { get; set; }
    public string Name { get; set; } = null!;
    public string Code { get; set; } = null!;
    public DateTimeOffset CodeGeneratedAt { get; set; }
    public DateTimeOffset CreatedAt { get; set; }

    public string CreatorEmail { get; set; } = "";
}
