using CourseSync.Api.Models;

namespace CourseSync.Api.Application.Courses;

public interface ICourseCommandService
{
    Task<(bool Ok, Guid? CourseId, string? Name, string GeneralInfo, IReadOnlyList<CourseContactPersonItem> Contacts, IReadOnlyList<CourseUsefulLinkItem> UsefulLinks, string? ErrorCode)> CreateCourseAsync(
        Guid userId,
        Guid groupId,
        string name,
        string generalInfo,
        IReadOnlyList<CourseContactPersonItem> contacts,
        IReadOnlyList<CourseUsefulLinkItem> usefulLinks,
        CancellationToken ct);

    Task<(bool Ok, string? ErrorCode)> UpdateCourseAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        string name,
        string generalInfo,
        IReadOnlyList<CourseContactPersonItem> contacts,
        IReadOnlyList<CourseUsefulLinkItem> usefulLinks,
        CancellationToken ct);

    Task<(bool Ok, string? ErrorCode)> DeleteCourseAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        CancellationToken ct);
}
