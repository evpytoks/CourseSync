namespace CourseSync.Api.Application.Courses;

public interface ICourseGradingService
{
    Task<(bool Ok, string? ErrorCode)> SaveGradingAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        string? text,
        IReadOnlyList<(string Name, decimal Coefficient, decimal Block)> elements,
        CancellationToken ct);

    Task<(bool Ok, string? Text, string? ErrorCode)> GetGradingTextAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        CancellationToken ct);

    Task<(bool Ok, IReadOnlyList<GradingElementDto>? Elements, string? ErrorCode)> GetGradingAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        CancellationToken ct);

    Task<(bool Ok, string Name, int Count, IReadOnlyList<decimal>? Scores, string? ErrorCode)> GetGradingScoresAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        string elementName,
        CancellationToken ct);

    Task<(bool Ok, IReadOnlyList<GradingElementScoresRow>? Elements, string? ErrorCode)> GetAllGradingScoresAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        CancellationToken ct);

    Task<(bool Ok, string? ErrorCode)> UpdateGradingScoresAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        string elementName,
        IReadOnlyList<decimal> scores,
        CancellationToken ct);

    Task<(bool Ok, IReadOnlyList<(Guid Id, string Name)>? Items, string? ErrorCode)> GetGradingElementOptionsAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        CancellationToken ct);
}
