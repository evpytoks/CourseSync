using CourseSync.Api.Data;
using CourseSync.Api.Models;

namespace CourseSync.Api.Services;

public interface IUserService
{
    Task<User?> FindByEmailAsync(string email, CancellationToken ct = default);
    Task<User?> FindByIdAsync(Guid id, CancellationToken ct = default);
    Task ClearCurrentGroupAsync(Guid userId, CancellationToken ct = default);
    Task<(bool Ok, string? ErrorCode)> UpdateUserSettingsAsync(
        Guid userId,
        bool? notificationsOn,
        bool? darkThemeOn,
        IReadOnlyList<UpdateCalendarEventTypeColorItem>? calendarEventTypeColors,
        CancellationToken ct = default);
    IReadOnlyList<(string Type, string Color)> GetResolvedCalendarEventTypeColors(User user);
    Task<User> GetOrCreateByEmailAsync(string email, CancellationToken ct = default);
}
