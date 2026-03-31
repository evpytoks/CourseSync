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
    [property: JsonPropertyName("name")] string Name);
public sealed record CourseListResponse(IReadOnlyList<CourseListItem> Courses);

public sealed record AddCourseRequest(
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("general_info")] string? GeneralInfo,
    [property: JsonPropertyName("useful_links")] string? UsefulLinks);
public sealed record AddCourseResponse(
    Guid Id,
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("general_info")] string GeneralInfo,
    [property: JsonPropertyName("useful_links")] string UsefulLinks);

public sealed record CourseDetailsResponse(
    Guid Id,
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("general_info")] string GeneralInfo,
    [property: JsonPropertyName("useful_links")] string UsefulLinks);

public sealed record ChangeCourseRequest(
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("general_info")] string? GeneralInfo,
    [property: JsonPropertyName("useful_links")] string? UsefulLinks);

public sealed record CourseMaterialListItem(
    Guid Id,
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("author_email")] string AuthorEmail,
    [property: JsonPropertyName("created_at")] DateTimeOffset CreatedAt);

public sealed record CourseMaterialListResponse(
    [property: JsonPropertyName("materials")] IReadOnlyList<CourseMaterialListItem> Materials);

public sealed record CoursePersonalMaterialListItem(
    Guid Id,
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("author_email")] string AuthorEmail,
    [property: JsonPropertyName("created_at")] DateTimeOffset CreatedAt,
    [property: JsonPropertyName("is_creator")] bool IsCreator);

public sealed record CoursePersonalMaterialListResponse(
    [property: JsonPropertyName("materials")] IReadOnlyList<CoursePersonalMaterialListItem> Materials);

public sealed record CourseGradingElementRequest(
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("coefficient")] decimal Coefficient);

public sealed record SaveCourseGradingRequest(
    [property: JsonPropertyName("text")] string? Text,
    [property: JsonPropertyName("elements")] IReadOnlyList<CourseGradingElementRequest>? Elements);

public sealed record CourseGradingTextResponse(
    [property: JsonPropertyName("text")] string Text);

public sealed record CourseGradingElementResponse(
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("coefficient")] decimal Coefficient,
    [property: JsonPropertyName("count")] int Count,
    [property: JsonPropertyName("average_score")] decimal AverageScore);

public sealed record CourseGradingResponse(
    [property: JsonPropertyName("elements")] IReadOnlyList<CourseGradingElementResponse> Elements,
    [property: JsonPropertyName("average_grade")] decimal AverageGrade);

public sealed record CourseGradingScoresResponse(
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("count")] int Count,
    [property: JsonPropertyName("scores")] IReadOnlyList<decimal> Scores);

public sealed record CourseGradingAllScoresResponse(
    [property: JsonPropertyName("elements")] IReadOnlyList<CourseGradingScoresResponse> Elements);

public sealed record UpdateCourseGradingScoresRequest(
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("scores")] IReadOnlyList<decimal> Scores);

public sealed record CalendarListItem(
    Guid Id,
    string Name,
    DateTime Date);
public sealed record CalendarListResponse(IReadOnlyList<CalendarListItem> Events);

public sealed record AddCalendarEventRequest(
    string Name,
    DateTime Date,
    [property: JsonPropertyName("description")] string? Description);

public sealed record CalendarEventDetailsResponse(
    string Name,
    DateTime Date,
    string Description);

public sealed record UpdateCalendarEventRequest(
    string Name,
    DateTime Date,
    [property: JsonPropertyName("description")] string? Description);

public sealed record RegisterDeviceRequest(
    string Platform,
    string Token);

public sealed record UnregisterDeviceRequest(
    string Token);

public sealed record NewsListItem(
    Guid Id,
    [property: JsonPropertyName("time")] DateTimeOffset Time,
    [property: JsonPropertyName("group")] string Group,
    [property: JsonPropertyName("section")] string Section,
    [property: JsonPropertyName("text")] string Text);

public sealed record NewsListResponse(IReadOnlyList<NewsListItem> News);

public sealed record NewsDetailsResponse(
    Guid Id,
    [property: JsonPropertyName("time")] DateTimeOffset Time,
    [property: JsonPropertyName("group")] string Group,
    [property: JsonPropertyName("section")] string Section,
    [property: JsonPropertyName("text")] string Text);

public sealed record AddNewsRequest(
    [property: JsonPropertyName("text")] string? Text);
