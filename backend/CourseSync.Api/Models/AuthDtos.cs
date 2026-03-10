using System.Text.Json.Serialization;

namespace CourseSync.Api.Models;

public sealed record SendCodeRequest(string Email);
public sealed record SendCodeResponse(string RequestId, DateTimeOffset ExpiresAt);

public sealed record LoginRequest(string Email, string RequestId, string Code);

public sealed record LoginResponse(string Token, string RefreshToken);

public sealed record RefreshRequest(string RefreshToken);
public sealed record RefreshResponse(string Token, string RefreshToken);

public sealed record ApiError([property: JsonPropertyName("error_code")] string Code);
public sealed record ErrorEnvelope(ApiError Error);

public sealed record CreateGroupRequest(string Name);
public sealed record CreateGroupResponse(Guid Id, string Name, string Code);
public sealed record GroupListItem(
    Guid Id,
    string Name,
    [property: JsonPropertyName("role")] string Role,
    [property: JsonPropertyName("group_code")] string? GroupCode);
public sealed record GroupListResponse(IReadOnlyList<GroupListItem> Groups);

public sealed record GroupJoinRequest(string Code);
public sealed record GroupJoinResponse(Guid GroupId, string Role);

public sealed record GroupChangeRequest(string Name);
public sealed record GroupChangeResponse(Guid Id, string Name);
public sealed record ChooseGroupResponse(Guid Id, string Name);

public sealed record GroupNameResponse(Guid Id, string Name);

public sealed record CourseListItem(
    Guid Id,
    string Name,
    [property: JsonPropertyName("general_info")] string GeneralInfo,
    [property: JsonPropertyName("useful_links")] string UsefulLinks);
public sealed record CourseListResponse(IReadOnlyList<CourseListItem> Courses);

public sealed record AddCourseRequest(
    string Name,
    [property: JsonPropertyName("general_info")] string? GeneralInfo,
    [property: JsonPropertyName("useful_links")] string? UsefulLinks);
public sealed record AddCourseResponse(
    Guid Id,
    string Name,
    [property: JsonPropertyName("general_info")] string GeneralInfo,
    [property: JsonPropertyName("useful_links")] string UsefulLinks);
