namespace CourseSync.Api.Data;

public sealed class CourseGradingElement
{
    public Guid Id { get; set; }
    public Guid CourseId { get; set; }
    public string Name { get; set; } = "";
    public decimal Coefficient { get; set; }
    public int Position { get; set; }
    public DateTimeOffset CreatedAt { get; set; }

    public Course Course { get; set; } = null!;
}
