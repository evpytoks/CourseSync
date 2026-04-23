using CourseSync.Api.Infrastructure;
using CourseSync.Api.Models;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

public sealed partial class CourseController
{
    [HttpGet("{id:guid}/general-materials")]
    public async Task<IActionResult> ListGeneralMaterials(Guid id, CancellationToken ct)
    {
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var (ok, items, errorCode) = await _courseMaterialService.ListGeneralAsync(userId, groupId, id, ct);

        if (!ok)
            return MaterialAccessError(errorCode!);

        var list = items!.Select(m => new CourseMaterialListItem(
            m.Id,
            m.Name,
            m.AuthorEmail,
            m.CreatedAt)).ToList();
        return Ok(new CourseMaterialListResponse(list));
    }

    [HttpPost("{id:guid}/general-materials")]
    [Consumes("multipart/form-data")]
    [RequestSizeLimit(CourseMaterialUploadLimits.MaxMultipartRequestBytes)]
    public async Task<IActionResult> AddGeneralMaterial(Guid id, IFormFile? file, CancellationToken ct)
    {
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        if (file is null)
            return BadRequest(new ErrorEnvelope(new ApiError("file_required")));

        var (ok, errorCode) = await _courseMaterialService.AddGeneralAsync(
            userId,
            groupId,
            id,
            user.Email,
            file,
            ct);

        if (!ok)
            return AddMaterialError(errorCode!);

        return NoContent();
    }

    [HttpGet("{id:guid}/personal-materials")]
    public async Task<IActionResult> ListPersonalMaterials(Guid id, CancellationToken ct)
    {
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var (ok, items, errorCode) = await _courseMaterialService.ListPersonalAsync(userId, groupId, id, ct);

        if (!ok)
            return MaterialAccessError(errorCode!);

        var list = items!.Select(m => new CoursePersonalMaterialListItem(
            m.Id,
            m.Name,
            m.AuthorEmail,
            m.CreatedAt,
            m.IsCreator)).ToList();
        return Ok(new CoursePersonalMaterialListResponse(list));
    }

    [HttpPost("{id:guid}/personal-materials")]
    [Consumes("multipart/form-data")]
    [RequestSizeLimit(CourseMaterialUploadLimits.MaxMultipartRequestBytes)]
    public async Task<IActionResult> AddPersonalMaterial(Guid id, IFormFile? file, CancellationToken ct)
    {
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        if (file is null)
            return BadRequest(new ErrorEnvelope(new ApiError("file_required")));

        var (ok, errorCode) = await _courseMaterialService.AddPersonalAsync(
            userId,
            groupId,
            id,
            user.Email,
            file,
            ct);

        if (!ok)
            return AddMaterialError(errorCode!);

        return NoContent();
    }

    [HttpDelete("{id:guid}/general-materials/{materialId:guid}")]
    public async Task<IActionResult> DeleteGeneralMaterial(Guid id, Guid materialId, CancellationToken ct)
    {
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var (ok, errorCode) = await _courseMaterialService.DeleteGeneralAsync(
            userId,
            groupId,
            id,
            materialId,
            ct);

        if (!ok)
            return DeleteMaterialError(errorCode!);

        return NoContent();
    }

    [HttpGet("{id:guid}/general-materials/{materialId:guid}/pdf")]
    public async Task<IActionResult> OpenGeneralMaterialPdf(Guid id, Guid materialId, CancellationToken ct)
    {
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var (ok, file, errorCode) = await _courseMaterialService.OpenGeneralPdfAsync(
            userId,
            groupId,
            id,
            materialId,
            ct);

        if (!ok)
            return DownloadMaterialError(errorCode!);

        return File(file!.Content, "application/pdf", file.FileName, enableRangeProcessing: true);
    }

    [HttpDelete("{id:guid}/personal-materials/{materialId:guid}")]
    public async Task<IActionResult> DeletePersonalMaterial(Guid id, Guid materialId, CancellationToken ct)
    {
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var (ok, errorCode) = await _courseMaterialService.DeletePersonalAsync(
            userId,
            groupId,
            id,
            materialId,
            ct);

        if (!ok)
            return DeleteMaterialError(errorCode!);

        return NoContent();
    }

    [HttpGet("{id:guid}/personal-materials/{materialId:guid}/pdf")]
    public async Task<IActionResult> OpenPersonalMaterialPdf(Guid id, Guid materialId, CancellationToken ct)
    {
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var (ok, file, errorCode) = await _courseMaterialService.OpenPersonalPdfAsync(
            userId,
            groupId,
            id,
            materialId,
            ct);

        if (!ok)
            return DownloadMaterialError(errorCode!);

        return File(file!.Content, "application/pdf", file.FileName, enableRangeProcessing: true);
    }
}
