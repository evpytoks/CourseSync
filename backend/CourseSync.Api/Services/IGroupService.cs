using CourseSync.Api.Models;

namespace CourseSync.Api.Services;

public interface IGroupService
{
    Task<List<GroupService.GroupListDto>> GetUserGroupsAsync(Guid userId, CancellationToken ct);
    Task<List<GroupService.OwnerGroupListDto>> GetOwnerGroupsAsync(Guid userId, CancellationToken ct);
    Task<(Guid GroupId, string Name, string Code)?> CreateGroupAsync(Guid ownerId, string name, CancellationToken ct);
    Task<JoinByCodeResult> JoinByCodeAsync(Guid userId, string code, CancellationToken ct);
    Task<(bool Ok, IReadOnlyList<GroupParticipantItem>? Items, string? ErrorCode)> GetParticipantEmailsForOwnerAsync(
        Guid ownerUserId,
        Guid groupId,
        CancellationToken ct);
    Task<(bool Ok, string? ErrorCode)> BlockParticipantByEmailAsync(Guid ownerUserId, Guid groupId, string email, CancellationToken ct);
    Task<(bool Ok, string? ErrorCode)> UnblockParticipantByEmailAsync(Guid ownerUserId, Guid groupId, string email, CancellationToken ct);
    Task<(bool Ok, string? ErrorCode)> ChangeNameAsync(Guid userId, Guid groupId, string name, CancellationToken ct);
    Task<(bool Ok, Guid? GroupId, string? Name)> ChooseGroupAsync(Guid userId, Guid groupId, CancellationToken ct);
    Task<(bool Ok, string? ErrorCode)> LeaveGroupAsync(Guid userId, Guid groupId, CancellationToken ct);
    Task<(bool Ok, string? ErrorCode)> DeleteGroupAsync(Guid userId, Guid groupId, CancellationToken ct);
    Task<(bool Ok, Guid Id, string Name, string Role, string? GroupCode, string? ErrorCode)> GetGroupDetailsAsync(Guid userId, CancellationToken ct);
}
