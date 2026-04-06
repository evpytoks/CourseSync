namespace CourseSync.Api.Data;

public sealed class CourseCumulativeGradeElement
{
    public Guid Id { get; set; }
    public Guid CourseId { get; set; }
    public Guid CourseGradingElementId { get; set; }
    public int Position { get; set; }

    public CourseCumulativeGrade CumulativeGrade { get; set; } = null!;
    public CourseGradingElement GradingElement { get; set; } = null!;
}
