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
    [property: JsonPropertyName("group_code")] string? GroupCode,
    [property: JsonPropertyName("creator_email")] string CreatorEmail);
public sealed record GroupListResponse(IReadOnlyList<GroupListItem> Groups);

public sealed record OwnerGroupListItem(Guid Id, string Name);

public sealed record OwnerGroupListResponse(IReadOnlyList<OwnerGroupListItem> Groups);

public sealed record GroupJoinRequest(string Code);

public sealed record SetCurrentGroupRequest([property: JsonPropertyName("group_id")] Guid GroupId);

public sealed record GroupChangeRequest(string Name);
public sealed record GroupChangeResponse(Guid Id, string Name);

public sealed record GroupDetailsResponse(
    Guid Id,
    string Name,
    [property: JsonPropertyName("role")] string Role,
    [property: JsonPropertyName("group_code")] string? GroupCode);

public sealed record GroupParticipantItem(
    [property: JsonPropertyName("email")] string Email,
    [property: JsonPropertyName("is_blocked")] bool IsBlocked);

public sealed record GroupParticipantsResponse(
    [property: JsonPropertyName("participants")] IReadOnlyList<GroupParticipantItem> Participants);

public sealed record GroupParticipantEmailRequest(
    [property: JsonPropertyName("email")] string Email);

public sealed record UserSettingsResponse(
    [property: JsonPropertyName("notifications_on")] bool NotificationsOn,
    [property: JsonPropertyName("dark_theme_on")] bool DarkThemeOn,
    [property: JsonPropertyName("calendar_event_type_colors")] IReadOnlyList<CalendarEventTypeColorItem> CalendarEventTypeColors);

public sealed record UpdateUserSettingsRequest(
    [property: JsonPropertyName("notifications_on")] bool? NotificationsOn,
    [property: JsonPropertyName("dark_theme_on")] bool? DarkThemeOn,
    [property: JsonPropertyName("calendar_event_type_colors")] IReadOnlyList<UpdateCalendarEventTypeColorItem>? CalendarEventTypeColors);

public sealed record CalendarEventTypeColorItem(
    [property: JsonPropertyName("type")] string Type,
    [property: JsonPropertyName("color")] string Color);

public sealed record CalendarEventTypeColorsResponse(
    [property: JsonPropertyName("items")] IReadOnlyList<CalendarEventTypeColorItem> Items);

public sealed record UpdateCalendarEventTypeColorItem(
    [property: JsonPropertyName("type")] string? Type,
    [property: JsonPropertyName("color")] string? Color);

public sealed record CourseListItem(
    Guid Id,
    [property: JsonPropertyName("name")] string Name);
public sealed record CourseListResponse(IReadOnlyList<CourseListItem> Courses);

public sealed record CourseUsefulLinkItem(
    [property: JsonPropertyName("title")] string Title,
    [property: JsonPropertyName("url")] string Url);

public sealed record CourseContactMethodItem(
    [property: JsonPropertyName("type")] string? Type,
    [property: JsonPropertyName("value")] string? Value);

public sealed record CourseContactPersonItem(
    [property: JsonPropertyName("name")] string? Name,
    [property: JsonPropertyName("contact_methods")] IReadOnlyList<CourseContactMethodItem>? ContactMethods);

public sealed record AddCourseRequest(
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("general_info")] string? GeneralInfo,
    [property: JsonPropertyName("contacts")] IReadOnlyList<CourseContactPersonItem>? Contacts,
    [property: JsonPropertyName("useful_links")] IReadOnlyList<CourseUsefulLinkItem>? UsefulLinks);
public sealed record AddCourseResponse(
    Guid Id,
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("general_info")] string GeneralInfo,
    [property: JsonPropertyName("contacts")] IReadOnlyList<CourseContactPersonItem> Contacts,
    [property: JsonPropertyName("useful_links")] IReadOnlyList<CourseUsefulLinkItem> UsefulLinks);

public sealed record CourseDetailsResponse(
    Guid Id,
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("general_info")] string GeneralInfo,
    [property: JsonPropertyName("contacts")] IReadOnlyList<CourseContactPersonItem> Contacts,
    [property: JsonPropertyName("useful_links")] IReadOnlyList<CourseUsefulLinkItem> UsefulLinks);

public sealed record ChangeCourseRequest(
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("general_info")] string? GeneralInfo,
    [property: JsonPropertyName("contacts")] IReadOnlyList<CourseContactPersonItem>? Contacts,
    [property: JsonPropertyName("useful_links")] IReadOnlyList<CourseUsefulLinkItem>? UsefulLinks);

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
    [property: JsonPropertyName("coefficient")] decimal Coefficient,
    [property: JsonPropertyName("block_grade")] decimal? Block);

public sealed record SaveCourseGradingRequest(
    [property: JsonPropertyName("text")] string? Text,
    [property: JsonPropertyName("elements")] IReadOnlyList<CourseGradingElementRequest>? Elements);

public sealed record CourseGradingTextResponse(
    [property: JsonPropertyName("text")] string Text);

public sealed record CourseGradingElementOptionItem(
    Guid Id,
    [property: JsonPropertyName("name")] string Name);

public sealed record CourseGradingElementListResponse(
    [property: JsonPropertyName("elements")] IReadOnlyList<CourseGradingElementOptionItem> Elements);

public sealed record SaveCourseCumulativeGradeRequest(
    [property: JsonPropertyName("element_ids")] IReadOnlyList<Guid>? ElementIds,
    [property: JsonPropertyName("block_grade")] decimal? Block,
    [property: JsonPropertyName("auto_grade")] decimal? Automatic);

public sealed record CourseCumulativeGradeResponse(
    [property: JsonPropertyName("value")] decimal Value,
    [property: JsonPropertyName("block_grade")] decimal Block,
    [property: JsonPropertyName("auto_grade")] decimal? Automatic,
    [property: JsonPropertyName("is_blocked")] bool IsBlocked,
    [property: JsonPropertyName("is_auto")] bool? IsAuto,
    [property: JsonPropertyName("element_names")] IReadOnlyList<string> ElementNames);

public sealed record CourseGradingElementResponse(
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("coefficient")] decimal Coefficient,
    [property: JsonPropertyName("block_grade")] decimal Block,
    [property: JsonPropertyName("count")] int Count,
    [property: JsonPropertyName("average_score")] decimal AverageScore,
    [property: JsonPropertyName("is_blocked")] bool IsBlocked);

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
    [property: JsonPropertyName("group_id")] Guid GroupId,
    [property: JsonPropertyName("group_name")] string GroupName,
    [property: JsonPropertyName("course_id")] Guid? CourseId,
    [property: JsonPropertyName("course_name")] string? CourseName,
    [property: JsonPropertyName("event_type")] string EventType,
    [property: JsonPropertyName("event_color")] string EventColor,
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("date")] DateTime Date,
    [property: JsonPropertyName("is_done")] bool IsDone);
public sealed record CalendarListResponse(IReadOnlyList<CalendarListItem> Events);

public sealed record AddCalendarEventRequest(
    [property: JsonPropertyName("group_id")] Guid GroupId,
    [property: JsonPropertyName("course_id")] Guid? CourseId,
    [property: JsonPropertyName("event_type")] string? EventType,
    [property: JsonPropertyName("name")] string? Name,
    [property: JsonPropertyName("date")] DateTime Date,
    [property: JsonPropertyName("description")] string? Description);

public sealed record CalendarEventDetailsResponse(
    [property: JsonPropertyName("group_id")] Guid GroupId,
    [property: JsonPropertyName("group_name")] string GroupName,
    [property: JsonPropertyName("course_id")] Guid? CourseId,
    [property: JsonPropertyName("course_name")] string? CourseName,
    [property: JsonPropertyName("event_type")] string EventType,
    [property: JsonPropertyName("event_color")] string EventColor,
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("date")] DateTime Date,
    [property: JsonPropertyName("description")] string Description,
    [property: JsonPropertyName("is_done")] bool IsDone);

public sealed record UpdateCalendarEventRequest(
    [property: JsonPropertyName("course_id")] Guid? CourseId,
    [property: JsonPropertyName("event_type")] string? EventType,
    [property: JsonPropertyName("name")] string? Name,
    [property: JsonPropertyName("date")] DateTime Date,
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
    [property: JsonPropertyName("text")] string Text,
    [property: JsonPropertyName("is_read")] bool IsRead);

public sealed record NewsListResponse(IReadOnlyList<NewsListItem> News);

public sealed record NewsUnreadCountResponse(
    [property: JsonPropertyName("unread_count")] int UnreadCount);

public sealed record NewsDetailsResponse(
    Guid Id,
    [property: JsonPropertyName("time")] DateTimeOffset Time,
    [property: JsonPropertyName("group")] string Group,
    [property: JsonPropertyName("section")] string Section,
    [property: JsonPropertyName("text")] string Text);

public sealed record AddNewsRequest(
    [property: JsonPropertyName("group_id")] Guid GroupId,
    [property: JsonPropertyName("text")] string? Text);

public sealed record MarkAllNewsReadResponse(
    [property: JsonPropertyName("marked_count")] int MarkedCount);
