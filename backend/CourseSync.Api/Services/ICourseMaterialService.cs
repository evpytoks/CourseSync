using Microsoft.AspNetCore.Http;

namespace CourseSync.Api.Services;

public interface ICourseMaterialService
{
    Task<(bool Ok, List<CourseMaterialService.MaterialListItemDto>? Items, string? ErrorCode)> ListGeneralAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        CancellationToken ct);
    Task<(bool Ok, List<CourseMaterialService.PersonalMaterialListItemDto>? Items, string? ErrorCode)> ListPersonalAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        CancellationToken ct);
    Task<(bool Ok, string? ErrorCode)> AddGeneralAsync(Guid userId, Guid groupId, Guid courseId, string userEmail, IFormFile file, CancellationToken ct);
    Task<(bool Ok, string? ErrorCode)> AddPersonalAsync(Guid userId, Guid groupId, Guid courseId, string userEmail, IFormFile file, CancellationToken ct);
    Task<(bool Ok, string? ErrorCode)> DeleteGeneralAsync(Guid userId, Guid groupId, Guid courseId, Guid materialId, CancellationToken ct);
    Task<(bool Ok, string? ErrorCode)> DeletePersonalAsync(Guid userId, Guid groupId, Guid courseId, Guid materialId, CancellationToken ct);
    Task<(bool Ok, CourseMaterialService.MaterialFileDto? File, string? ErrorCode)> OpenGeneralPdfAsync(Guid userId, Guid groupId, Guid courseId, Guid materialId, CancellationToken ct);
    Task<(bool Ok, CourseMaterialService.MaterialFileDto? File, string? ErrorCode)> OpenPersonalPdfAsync(Guid userId, Guid groupId, Guid courseId, Guid materialId, CancellationToken ct);
}
