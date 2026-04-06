using System.Security.Cryptography;
using System.Text.RegularExpressions;
using CourseSync.Api.Data;
using CourseSync.Api.Infrastructure;
using CourseSync.Api.Infrastructure.Storage;
using Microsoft.EntityFrameworkCore;
using System.Linq;
using Npgsql;

namespace CourseSync.Api.Services;

public sealed class GroupService
{
    private static readonly Regex GroupNameRegex = new(@"^[a-zA-Zа-яА-ЯёЁ0-9]{1,20}$", RegexOptions.Compiled);
    private const string CodeChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private const int CodeLength = 6;
    private const int CodeCollisionRetryCount = 5;

    private readonly AppDbContext _db;
    private readonly NotificationService _notifications;
    private readonly ICourseMaterialBlobStorage _materialBlobs;

    public GroupService(AppDbContext db, NotificationService notifications, ICourseMaterialBlobStorage materialBlobs)
    {
        _db = db;
        _notifications = notifications;
        _materialBlobs = materialBlobs;
    }

    public static (bool Valid, string? ErrorCode) ValidateGroupName(string? name)
    {
        if (string.IsNullOrWhiteSpace(name))
            return (false, "group_name_required");
        name = name.Trim();
        if (name.Length > 20)
            return (false, "group_name_too_long");
        if (!GroupNameRegex.IsMatch(name))
            return (false, "group_name_invalid");
        return (true, null);
    }

    public static (bool Valid, string? ErrorCode) ValidateGroupCode(string? code)
    {
        code = (code ?? "").Trim();
        if (code.Length != CodeLength)
            return (false, "invalid_code_format");
        if (!code.All(c => CodeChars.Contains(c)))
            return (false, "invalid_code_format");
        return (true, null);
    }

    public async Task<(Guid GroupId, string Name, string Code)?> CreateGroupAsync(Guid ownerId, string name, CancellationToken ct)
    {
        var nameTrimmed = name.Trim();
        var creatorEmail = await _db.Users.AsNoTracking()
            .Where(u => u.Id == ownerId)
            .Select(u => u.Email)
            .FirstOrDefaultAsync(ct) ?? "";
        for (var attempt = 0; attempt < CodeCollisionRetryCount; attempt++)
        {
            var group = new CourseSync.Api.Data.Group
            {
                Id = Guid.NewGuid(),
                Name = nameTrimmed,
                Code = GenerateCode(),
                CodeGeneratedAt = DateTimeOffset.UtcNow,
                CreatedAt = DateTimeOffset.UtcNow,
                CreatorEmail = creatorEmail
            };
            _db.Groups.Add(group);
            _db.GroupMembers.Add(new GroupMember
            {
                GroupId = group.Id,
                UserId = ownerId,
                Role = GroupRole.Owner,
                JoinedAt = group.CreatedAt
            });
            try
            {
                await _db.SaveChangesAsync(ct);
                return (group.Id, group.Name, group.Code);
            }
            catch (DbUpdateException ex) when (IsUniqueCodeViolation(ex) && attempt < CodeCollisionRetryCount - 1)
            {
                _db.ChangeTracker.Clear();
            }
        }
        return null;
    }

    public async Task<string> GetOrRefreshCodeAsync(CourseSync.Api.Data.Group group, CancellationToken ct)
    {
        var today = DateTimeOffset.UtcNow.Date;
        var generatedDate = group.CodeGeneratedAt.UtcDateTime.Date;
        if (generatedDate >= today)
            return group.Code;

        for (var attempt = 0; attempt < CodeCollisionRetryCount; attempt++)
        {
            var newCode = GenerateCode();
            var entity = await _db.Groups.FirstOrDefaultAsync(x => x.Id == group.Id, ct);
            if (entity is null) continue;
            entity.Code = newCode;
            entity.CodeGeneratedAt = DateTimeOffset.UtcNow;
            try
            {
                await _db.SaveChangesAsync(ct);
                return newCode;
            }
            catch (DbUpdateException ex) when (IsUniqueCodeViolation(ex) && attempt < CodeCollisionRetryCount - 1)
            {
               
            }
        }
        throw new InvalidOperationException("Failed to generate unique group code after retries.");
    }

    public async Task<List<GroupListDto>> GetUserGroupsAsync(Guid userId, CancellationToken ct)
    {
        var members = await _db.GroupMembers
            .Include(m => m.Group)
            .Where(m => m.UserId == userId)
            .OrderBy(m => m.Group!.Name)
            .ToListAsync(ct);

        var result = new List<GroupListDto>();
        foreach (var m in members)
        {
            var codeStr = m.Role == GroupRole.Owner ? await GetOrRefreshCodeAsync(m.Group, ct) : null;
            result.Add(new GroupListDto(
                m.Group!.Id,
                m.Group.Name,
                m.Role == GroupRole.Owner ? "owner" : "participant",
                codeStr,
                m.Group.CreatorEmail ?? ""
            ));
        }
        return result;
    }

    public async Task<List<OwnerGroupListDto>> GetOwnerGroupsAsync(Guid userId, CancellationToken ct)
    {
        return await _db.GroupMembers
            .AsNoTracking()
            .Where(m => m.UserId == userId && m.Role == GroupRole.Owner)
            .OrderBy(m => m.Group!.Name)
            .Select(m => new OwnerGroupListDto(m.Group!.Id, m.Group.Name))
            .ToListAsync(ct);
    }

    private static string GenerateCode()
    {
        var bytes = RandomNumberGenerator.GetBytes(CodeLength);
        var chars = new char[CodeLength];
        for (var i = 0; i < CodeLength; i++)
            chars[i] = CodeChars[bytes[i] % CodeChars.Length];
        return new string(chars);
    }

    public sealed record GroupListDto(Guid Id, string Name, string Role, string? GroupCode, string CreatorEmail);

    public sealed record OwnerGroupListDto(Guid Id, string Name);

    public async Task<(Guid GroupId, string Name, string Role)?> JoinByCodeAsync(Guid userId, string code, CancellationToken ct)
    {
        code = (code ?? "").Trim();
        if (code.Length != CodeLength || !code.All(c => CodeChars.Contains(c)))
            return null;

        var group = await _db.Groups.FirstOrDefaultAsync(g => g.Code == code, ct);
        if (group is null)
            return null;

        var existing = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == group.Id && m.UserId == userId, ct);
        if (existing is not null)
            return (group.Id, group.Name, existing.Role == GroupRole.Owner ? "owner" : "participant");

        _db.GroupMembers.Add(new GroupMember
        {
            GroupId = group.Id,
            UserId = userId,
            Role = GroupRole.Participant,
            JoinedAt = DateTimeOffset.UtcNow
        });
        await _db.SaveChangesAsync(ct);

        var joinerEmail = await _db.Users.AsNoTracking()
            .Where(u => u.Id == userId)
            .Select(u => u.Email)
            .FirstOrDefaultAsync(ct) ?? "";

        await _notifications.CreateNewsAndPushToGroupOwnersAsync(
            "member_joined_by_code",
            group.Id,
            group.Name,
            NewsFormatting.SectionGroups,
            NewsFormatting.DetailMemberJoinedByCode(joinerEmail),
            ct,
            userId);

        return (group.Id, group.Name, "participant");
    }

    public async Task<(bool Ok, string? ErrorCode)> ChangeNameAsync(Guid userId, Guid groupId, string name, CancellationToken ct)
    {
        var validation = ValidateGroupName(name);
        if (!validation.Valid)
            return (false, validation.ErrorCode);

        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null || member.Role != GroupRole.Owner)
            return (false, "forbidden");

        var group = await _db.Groups.FirstOrDefaultAsync(g => g.Id == groupId, ct);
        if (group is null) return (false, "forbidden");
        var oldName = group.Name;
        group.Name = name.Trim();
        await _db.SaveChangesAsync(ct);

        await _notifications.CreateNewsAndPushAsync(
            "group_renamed",
            userId,
            groupId,
            group.Name,
            NewsFormatting.SectionGroups,
            NewsFormatting.DetailGroupRenamed(oldName, group.Name),
            ct);

        return (true, null);
    }

    public async Task<(bool Ok, Guid? GroupId, string? Name)> ChooseGroupAsync(Guid userId, Guid groupId, CancellationToken ct)
    {
        var member = await _db.GroupMembers
            .Include(m => m.Group)
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null)
            return (false, null, null);

        var user = await _db.Users.FirstOrDefaultAsync(u => u.Id == userId, ct);
        if (user is null) return (false, null, null);
        user.CurrentGroupId = groupId;
        await _db.SaveChangesAsync(ct);
        return (true, groupId, member.Group!.Name);
    }

    public async Task<(bool Ok, string? ErrorCode)> LeaveGroupAsync(Guid userId, Guid groupId, CancellationToken ct)
    {
        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null)
            return (false, "not_in_group");

        if (member.Role == GroupRole.Owner)
            return await DeleteGroupAsync(userId, groupId, ct);

        _db.GroupMembers.Remove(member);
        var user = await _db.Users.FirstOrDefaultAsync(u => u.Id == userId, ct);
        if (user is not null && user.CurrentGroupId == groupId)
            user.CurrentGroupId = null;
        await _db.SaveChangesAsync(ct);
        return (true, null);
    }

    public async Task<(bool Ok, string? ErrorCode)> DeleteGroupAsync(Guid userId, Guid groupId, CancellationToken ct)
    {
        var groupExists = await _db.Groups.AnyAsync(g => g.Id == groupId, ct);
        if (!groupExists)
            return (false, "group_not_found");

        var member = await _db.GroupMembers
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null || member.Role != GroupRole.Owner)
            return (false, "forbidden");

        var courseIds = await _db.Courses.Where(c => c.GroupId == groupId).Select(c => c.Id).ToListAsync(ct);
        if (courseIds.Count > 0)
        {
            var genPaths = await _db.CourseGeneralMaterials
                .Where(m => courseIds.Contains(m.CourseId))
                .Select(m => m.StoragePath)
                .ToListAsync(ct);
            var perPaths = await _db.CoursePersonalMaterials
                .Where(m => courseIds.Contains(m.CourseId))
                .Select(m => m.StoragePath)
                .ToListAsync(ct);
            foreach (var path in genPaths.Concat(perPaths).Distinct())
            {
                try
                {
                    await _materialBlobs.DeleteAsync(path, ct);
                }
                catch
                {
                }
            }
        }

        var group = await _db.Groups.FirstAsync(g => g.Id == groupId, ct);
        _db.Groups.Remove(group);
        await _db.SaveChangesAsync(ct);
        return (true, null);
    }

    public async Task<(bool Ok, Guid Id, string Name, string Role, string? GroupCode, string? ErrorCode)> GetGroupDetailsAsync(Guid userId, CancellationToken ct)
    {
        var user = await _db.Users.FirstOrDefaultAsync(u => u.Id == userId, ct);
        if (user is null || user.CurrentGroupId is null)
            return (false, Guid.Empty, "", "", null, "no_group_selected");

        var groupId = user.CurrentGroupId.Value;

        var member = await _db.GroupMembers
            .Include(m => m.Group)
            .FirstOrDefaultAsync(m => m.GroupId == groupId && m.UserId == userId, ct);
        if (member is null || member.Group is null)
            return (false, Guid.Empty, "", "", null, "forbidden");

        var role = member.Role == GroupRole.Owner ? "owner" : "participant";
        string? codeStr = null;
        if (member.Role == GroupRole.Owner)
            codeStr = await GetOrRefreshCodeAsync(member.Group, ct);

        return (true, member.Group.Id, member.Group.Name, role, codeStr, null);
    }

    private static bool IsUniqueCodeViolation(DbUpdateException ex)
    {
        for (var e = ex.InnerException; e != null; e = e.InnerException)
        {
            if (e is PostgresException pg && pg.SqlState == "23505")
                return true;
        }
        return false;
    }
}
