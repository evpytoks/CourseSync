namespace CourseSync.Api.Application.Courses;

public interface ICourseCumulativeGradeService
{
    Task<(bool Ok, string? ErrorCode)> SaveCumulativeGradeAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        IReadOnlyList<Guid>? elementIds,
        decimal? block,
        decimal? automatic,
        CancellationToken ct);

    Task<(bool Ok, CourseCumulativeGradeDetailDto? Data, string? ErrorCode)> GetCumulativeGradeAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        CancellationToken ct);
}
