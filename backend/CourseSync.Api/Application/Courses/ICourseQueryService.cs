namespace CourseSync.Api.Application.Courses;

public interface ICourseQueryService
{
    Task<List<CourseListDto>> GetByGroupIdAsync(Guid groupId, CancellationToken ct);

    Task<(bool Ok, List<CourseListDto>? Courses, string? ErrorCode)> GetByGroupIdForOwnerAsync(
        Guid userId,
        Guid groupId,
        CancellationToken ct);

    Task<(bool Ok, CourseDetailDto? Data, string? ErrorCode)> GetCourseByIdAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        CancellationToken ct);
}
