namespace CourseSync.Api.Data;

public sealed class CourseGradingScore
{
    public Guid Id { get; set; }
    public Guid UserId { get; set; }
    public Guid CourseGradingElementId { get; set; }
    public int Number { get; set; }
    public decimal Score { get; set; }

    public User User { get; set; } = null!;
    public CourseGradingElement Element { get; set; } = null!;
}
