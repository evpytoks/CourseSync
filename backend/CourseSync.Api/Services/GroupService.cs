using System.Security.Cryptography;
using System.Text.RegularExpressions;
using CourseSync.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Services;

public sealed class GroupService
{
    private static readonly Regex GroupNameRegex = new(@"^[a-zA-Zа-яА-ЯёЁ0-9]{1,20}$", RegexOptions.Compiled);
    private const string CodeChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private const int CodeLength = 6;

    private readonly AppDbContext _db;

    public GroupService(AppDbContext db) => _db = db;

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

    public async Task<(Guid GroupId, string Name, string Code)?> CreateGroupAsync(Guid ownerId, string name, CancellationToken ct)
    {
        var group = new CourseSync.Api.Data.Group
        {
            Id = Guid.NewGuid(),
            Name = name.Trim(),
            Code = GenerateCode(),
            CodeGeneratedAt = DateTimeOffset.UtcNow,
            CreatedAt = DateTimeOffset.UtcNow
        };
        _db.Groups.Add(group);
        _db.GroupMembers.Add(new GroupMember
        {
            GroupId = group.Id,
            UserId = ownerId,
            Role = GroupRole.Owner,
            JoinedAt = group.CreatedAt
        });
        await _db.SaveChangesAsync(ct);
        return (group.Id, group.Name, group.Code);
    }

    public async Task<string> GetOrRefreshCodeAsync(CourseSync.Api.Data.Group group, CancellationToken ct)
    {
        var today = DateTimeOffset.UtcNow.Date;
        var generatedDate = group.CodeGeneratedAt.UtcDateTime.Date;
        if (generatedDate >= today)
            return group.Code;

        var newCode = GenerateCode();
        await _db.Groups
            .Where(x => x.Id == group.Id)
            .ExecuteUpdateAsync(s => s
                .SetProperty(x => x.Code, newCode)
                .SetProperty(x => x.CodeGeneratedAt, DateTimeOffset.UtcNow), ct);
        return newCode;
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
                codeStr
            ));
        }
        return result;
    }

    private static string GenerateCode()
    {
        var bytes = RandomNumberGenerator.GetBytes(CodeLength);
        var chars = new char[CodeLength];
        for (var i = 0; i < CodeLength; i++)
            chars[i] = CodeChars[bytes[i] % CodeChars.Length];
        return new string(chars);
    }

    public sealed record GroupListDto(Guid Id, string Name, string Role, string? GroupCode);
}
