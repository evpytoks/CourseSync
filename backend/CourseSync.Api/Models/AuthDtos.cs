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
public sealed record CreateGroupResponse(Guid Id, string Name);
public sealed record GroupListItem(
    Guid Id,
    string Name,
    [property: JsonPropertyName("role")] string Role,
    [property: JsonPropertyName("group_code")] string? GroupCode);
public sealed record GroupListResponse(IReadOnlyList<GroupListItem> Groups);

public sealed record GroupJoinRequest(string Code);

public sealed record GroupChangeRequest(string Name);
public sealed record GroupChangeResponse(Guid Id, string Name);

public sealed record GroupDetailsResponse(
    Guid Id,
    string Name,
    [property: JsonPropertyName("role")] string Role,
    [property: JsonPropertyName("group_code")] string? GroupCode);

public sealed record UserSettingsResponse(
    [property: JsonPropertyName("notifications_on")] bool NotificationsOn,
    [property: JsonPropertyName("dark_theme_on")] bool DarkThemeOn);

public sealed record UpdateUserSettingsRequest(
    [property: JsonPropertyName("notifications_on")] bool? NotificationsOn,
    [property: JsonPropertyName("dark_theme_on")] bool? DarkThemeOn);

public sealed record CourseListItem(
    Guid Id,
    string Name);
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
