using CourseSync.Api.Data;
using CourseSync.Api.Infrastructure;
using CourseSync.Api.Infrastructure.Storage;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Services;

public sealed class CourseMaterialService
{
    private const long MaxPdfBytes = CourseMaterialUploadLimits.MaxPdfBytes;

    private readonly AppDbContext _db;
    private readonly ICourseMaterialBlobStorage _blob;
    private readonly NotificationService _notifications;

    public CourseMaterialService(AppDbContext db, ICourseMaterialBlobStorage blob, NotificationService notifications)
    {
        _db = db;
        _blob = blob;
        _notifications = notifications;
    }

    public sealed record MaterialListItemDto(
        Guid Id,
        string Name,
        string AuthorEmail,
        DateTimeOffset CreatedAt);
    public sealed record PersonalMaterialListItemDto(
        Guid Id,
        string Name,
        string AuthorEmail,
        DateTimeOffset CreatedAt,
        bool IsCreator);
    public sealed record MaterialFileDto(Stream Content, string FileName);

    public async Task<(bool Ok, List<MaterialListItemDto>? Items, string? ErrorCode)> ListGeneralAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        CancellationToken ct)
    {
        var access = await CheckCourseAccessAsync(userId, groupId, courseId, ct);
        if (!access.Ok)
            return (false, null, access.ErrorCode);

        var items = await _db.CourseGeneralMaterials
            .AsNoTracking()
            .Where(m => m.CourseId == courseId)
            .OrderByDescending(m => m.CreatedAt)
            .Select(m => new MaterialListItemDto(m.Id, m.Name, m.AuthorEmail, m.CreatedAt))
            .ToListAsync(ct);
        return (true, items, null);
    }

    public async Task<(bool Ok, List<PersonalMaterialListItemDto>? Items, string? ErrorCode)> ListPersonalAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        CancellationToken ct)
    {
        var access = await CheckCourseAccessAsync(userId, groupId, courseId, ct);
        if (!access.Ok)
            return (false, null, access.ErrorCode);

        var items = await _db.CoursePersonalMaterials
            .AsNoTracking()
            .Where(m => m.CourseId == courseId)
            .OrderByDescending(m => m.CreatedAt)
            .Select(m => new PersonalMaterialListItemDto(m.Id, m.Name, m.AuthorEmail, m.CreatedAt, m.AuthorUserId == userId))
            .ToListAsync(ct);
        return (true, items, null);
    }

    public async Task<(bool Ok, string? ErrorCode)> AddGeneralAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        string userEmail,
        IFormFile file,
        CancellationToken ct)
    {
        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null)
            return (false, "forbidden");

        if (member.Role != GroupRole.Owner)
            return (false, "forbidden");

        var courseOk = await _db.Courses.AnyAsync(c => c.Id == courseId && c.GroupId == groupId, ct);
        if (!courseOk)
            return (false, "course_not_in_group");

        var fileError = await ValidatePdfFileAsync(file, ct);
        if (fileError is not null)
            return (false, fileError);

        var name = SanitizeFileName(file.FileName);
        var id = Guid.NewGuid();
        var objectKey = $"general/{courseId}/{id}.pdf";

        try
        {
            await using var inStream = file.OpenReadStream();
            await _blob.UploadAsync(inStream, objectKey, ct);

            var entity = new CourseGeneralMaterial
            {
                Id = id,
                CourseId = courseId,
                Name = name,
                AuthorUserId = userId,
                AuthorEmail = userEmail,
                StoragePath = objectKey,
                CreatedAt = DateTimeOffset.UtcNow
            };
            _db.CourseGeneralMaterials.Add(entity);
            await _db.SaveChangesAsync(ct);
        }
        catch
        {
            try
            {
                await _blob.DeleteAsync(objectKey, ct);
            }
            catch
            {
            }

            throw;
        }

        {
            var course = await _db.Courses.AsNoTracking()
                .FirstOrDefaultAsync(c => c.Id == courseId, ct);
            var courseLabel = course?.Name ?? "";
            var groupName = await GetGroupNameAsync(groupId, ct);
            await _notifications.CreateNewsAndPushAsync(
                "general_material_added",
                userId,
                groupId,
                groupName,
                NewsFormatting.SectionCourses,
                NewsFormatting.DetailGeneralMaterialAdded(courseLabel, name),
                ct);
        }

        return (true, null);
    }

    public async Task<(bool Ok, string? ErrorCode)> AddPersonalAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        string userEmail,
        IFormFile file,
        CancellationToken ct)
    {
        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null)
            return (false, "forbidden");

        var courseOk = await _db.Courses.AnyAsync(c => c.Id == courseId && c.GroupId == groupId, ct);
        if (!courseOk)
            return (false, "course_not_in_group");

        var fileError = await ValidatePdfFileAsync(file, ct);
        if (fileError is not null)
            return (false, fileError);

        var name = SanitizeFileName(file.FileName);
        var id = Guid.NewGuid();
        var objectKey = $"personal/{courseId}/{id}.pdf";

        try
        {
            await using var inStream = file.OpenReadStream();
            await _blob.UploadAsync(inStream, objectKey, ct);

            var entity = new CoursePersonalMaterial
            {
                Id = id,
                CourseId = courseId,
                Name = name,
                AuthorUserId = userId,
                AuthorEmail = userEmail,
                StoragePath = objectKey,
                CreatedAt = DateTimeOffset.UtcNow
            };
            _db.CoursePersonalMaterials.Add(entity);
            await _db.SaveChangesAsync(ct);
        }
        catch
        {
            try
            {
                await _blob.DeleteAsync(objectKey, ct);
            }
            catch
            {
            }

            throw;
        }

        {
            var course = await _db.Courses.AsNoTracking()
                .FirstOrDefaultAsync(c => c.Id == courseId, ct);
            var courseLabel = course?.Name ?? "";
            var groupName = await GetGroupNameAsync(groupId, ct);
            await _notifications.CreateNewsAndPushToAllMembersExceptAsync(
                "personal_material_added",
                groupId,
                groupName,
                NewsFormatting.SectionCourses,
                NewsFormatting.DetailPersonalMaterialAdded(courseLabel, userEmail, name),
                userId,
                ct);
        }

        return (true, null);
    }

    public async Task<(bool Ok, string? ErrorCode)> DeleteGeneralAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        Guid materialId,
        CancellationToken ct)
    {
        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null)
            return (false, "forbidden");

        if (member.Role != GroupRole.Owner)
            return (false, "forbidden");

        var courseOk = await _db.Courses.AnyAsync(c => c.Id == courseId && c.GroupId == groupId, ct);
        if (!courseOk)
            return (false, "course_not_in_group");

        var entity = await _db.CourseGeneralMaterials
            .FirstOrDefaultAsync(m => m.Id == materialId && m.CourseId == courseId, ct);
        if (entity is null)
            return (false, "material_not_found");

        _db.CourseGeneralMaterials.Remove(entity);
        await _db.SaveChangesAsync(ct);
        try
        {
            await _blob.DeleteAsync(entity.StoragePath, ct);
        }
        catch
        {
        }

        return (true, null);
    }

    public async Task<(bool Ok, string? ErrorCode)> DeletePersonalAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        Guid materialId,
        CancellationToken ct)
    {
        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null)
            return (false, "forbidden");

        var courseOk = await _db.Courses.AnyAsync(c => c.Id == courseId && c.GroupId == groupId, ct);
        if (!courseOk)
            return (false, "course_not_in_group");

        var entity = await _db.CoursePersonalMaterials
            .FirstOrDefaultAsync(m => m.Id == materialId && m.CourseId == courseId, ct);
        if (entity is null)
            return (false, "material_not_found");

        var canDelete = member.Role == GroupRole.Owner || entity.AuthorUserId == userId;
        if (!canDelete)
            return (false, "forbidden");

        _db.CoursePersonalMaterials.Remove(entity);
        await _db.SaveChangesAsync(ct);
        try
        {
            await _blob.DeleteAsync(entity.StoragePath, ct);
        }
        catch
        {
        }

        return (true, null);
    }

    private async Task<string> GetGroupNameAsync(Guid groupId, CancellationToken ct)
    {
        var name = await _db.Groups.AsNoTracking()
            .Where(g => g.Id == groupId)
            .Select(g => g.Name)
            .FirstOrDefaultAsync(ct);
        return string.IsNullOrWhiteSpace(name) ? "Группа" : name.Trim();
    }

    public async Task<(bool Ok, MaterialFileDto? File, string? ErrorCode)> OpenGeneralPdfAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        Guid materialId,
        CancellationToken ct)
    {
        var access = await CheckCourseAccessAsync(userId, groupId, courseId, ct);
        if (!access.Ok)
            return (false, null, access.ErrorCode);

        var entity = await _db.CourseGeneralMaterials
            .AsNoTracking()
            .FirstOrDefaultAsync(m => m.Id == materialId && m.CourseId == courseId, ct);
        if (entity is null)
            return (false, null, "material_not_found");

        var stream = await _blob.OpenReadAsync(entity.StoragePath, ct);
        if (stream is null)
            return (false, null, "material_not_found");

        return (true, new MaterialFileDto(stream, entity.Name), null);
    }

    public async Task<(bool Ok, MaterialFileDto? File, string? ErrorCode)> OpenPersonalPdfAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        Guid materialId,
        CancellationToken ct)
    {
        var access = await CheckCourseAccessAsync(userId, groupId, courseId, ct);
        if (!access.Ok)
            return (false, null, access.ErrorCode);

        var entity = await _db.CoursePersonalMaterials
            .AsNoTracking()
            .FirstOrDefaultAsync(m => m.Id == materialId && m.CourseId == courseId, ct);
        if (entity is null)
            return (false, null, "material_not_found");

        var stream = await _blob.OpenReadAsync(entity.StoragePath, ct);
        if (stream is null)
            return (false, null, "material_not_found");

        return (true, new MaterialFileDto(stream, entity.Name), null);
    }

    private async Task<(bool Ok, string? ErrorCode)> CheckCourseAccessAsync(
        Guid userId,
        Guid groupId,
        Guid courseId,
        CancellationToken ct)
    {
        var isMember = await _db.GroupMembers.AnyAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (!isMember)
            return (false, "forbidden");

        var courseOk = await _db.Courses.AnyAsync(c => c.Id == courseId && c.GroupId == groupId, ct);
        if (!courseOk)
            return (false, "course_not_in_group");

        return (true, null);
    }

    private static async Task<string?> ValidatePdfFileAsync(IFormFile file, CancellationToken ct)
    {
        if (file is null || file.Length == 0)
            return "file_required";
        if (file.Length > MaxPdfBytes)
            return "file_too_large";

        var ext = Path.GetExtension(file.FileName);
        if (!string.Equals(ext, ".pdf", StringComparison.OrdinalIgnoreCase))
            return "not_pdf";

        await using var stream = file.OpenReadStream();
        var buf = new byte[4];
        var n = await stream.ReadAsync(buf.AsMemory(0, 4), ct);
        if (n < 4 || buf[0] != (byte)'%' || buf[1] != (byte)'P' || buf[2] != (byte)'D' || buf[3] != (byte)'F')
            return "not_pdf";

        return null;
    }

    private static string SanitizeFileName(string? fileName)
    {
        var n = Path.GetFileName(fileName ?? "");
        if (string.IsNullOrWhiteSpace(n))
            n = "document.pdf";
        if (n.Length > 255)
            n = n[..255];
        return n;
    }

}
