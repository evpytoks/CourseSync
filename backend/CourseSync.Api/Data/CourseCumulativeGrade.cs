namespace CourseSync.Api.Data;

public sealed class CourseCumulativeGrade
{
    public Guid CourseId { get; set; }
    public decimal? Block { get; set; }
    public decimal? AutomaticThreshold { get; set; }
    public DateTimeOffset UpdatedAt { get; set; }

    public Course Course { get; set; } = null!;
    public ICollection<CourseCumulativeGradeElement> Elements { get; set; } = new List<CourseCumulativeGradeElement>();
}
