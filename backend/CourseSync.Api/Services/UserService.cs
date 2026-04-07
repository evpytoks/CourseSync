using CourseSync.Api.Data;
using CourseSync.Api.Infrastructure;
using CourseSync.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Services;

public sealed class UserService
{
    private readonly AppDbContext _db;
    public UserService(AppDbContext db) => _db = db;

    public async Task<User?> FindByEmailAsync(string email, CancellationToken ct = default)
    {
        email = email.Trim().ToLowerInvariant();
        return await _db.Users.SingleOrDefaultAsync(x => x.Email == email, ct);
    }

    public async Task<User?> FindByIdAsync(Guid id, CancellationToken ct = default)
        => await _db.Users.SingleOrDefaultAsync(x => x.Id == id, ct);

    public async Task ClearCurrentGroupAsync(Guid userId, CancellationToken ct = default)
    {
        var user = await _db.Users.FirstOrDefaultAsync(u => u.Id == userId, ct);
        if (user is null) return;
        user.CurrentGroupId = null;
        await _db.SaveChangesAsync(ct);
    }

    public async Task<(bool Ok, string? ErrorCode)> UpdateUserSettingsAsync(
        Guid userId,
        bool? notificationsOn,
        bool? darkThemeOn,
        IReadOnlyList<UpdateCalendarEventTypeColorItem>? calendarEventTypeColors,
        CancellationToken ct = default)
    {
        var user = await _db.Users.FirstOrDefaultAsync(u => u.Id == userId, ct);
        if (user is null)
            return (false, "unauthorized");

        if (notificationsOn is not null)
            user.NotificationsOn = notificationsOn.Value;

        if (darkThemeOn is not null)
            user.DarkThemeOn = darkThemeOn.Value;

        if (calendarEventTypeColors is not null)
        {
            var merge = ValidateAndMergeCalendarColorItems(calendarEventTypeColors, user.CalendarEventTypeColors);
            if (!merge.Valid)
                return (false, merge.ErrorCode);
            user.CalendarEventTypeColors = merge.Storage;
        }

        await _db.SaveChangesAsync(ct);
        return (true, null);
    }

    private static (bool Valid, string? ErrorCode, string Storage) ValidateAndMergeCalendarColorItems(
        IReadOnlyList<UpdateCalendarEventTypeColorItem> items,
        string existingStorage)
    {
        if (items.Count == 0)
            return (true, null, existingStorage);

        var seen = new HashSet<string>(StringComparer.Ordinal);
        var updates = new List<(string Type, string Color)>();
        foreach (var item in items)
        {
            if (!CalendarEventCatalog.TryNormalizeType(item.Type, out var eventType))
                return (false, "calendar_event_type_invalid", existingStorage);

            if (!seen.Add(eventType))
                return (false, "calendar_event_type_duplicate", existingStorage);

            var color = (item.Color ?? "").Trim();
            if (!IsHexColorCode(color))
                return (false, "calendar_event_color_invalid", existingStorage);

            updates.Add((eventType, color));
        }

        var current = new Dictionary<string, string>(CalendarEventTypeColorsCodec.FromStorage(existingStorage), StringComparer.Ordinal);
        foreach (var (type, color) in updates)
            current[type] = color;

        return (true, null, CalendarEventTypeColorsCodec.ToStorage(current));
    }

    public static bool IsHexColorCode(string value)
    {
        if (value.Length != 7 || value[0] != '#')
            return false;
        for (var i = 1; i < value.Length; i++)
        {
            var c = value[i];
            var isDigit = c is >= '0' and <= '9';
            var isHexLetter = c is >= 'a' and <= 'f' or >= 'A' and <= 'F';
            if (!isDigit && !isHexLetter)
                return false;
        }
        return true;
    }

    public IReadOnlyList<(string Type, string Color)> GetResolvedCalendarEventTypeColors(User user)
    {
        var overrides = CalendarEventTypeColorsCodec.FromStorage(user.CalendarEventTypeColors);
        return ResolveCalendarEventTypeColors(overrides);
    }

    public static IReadOnlyList<(string Type, string Color)> ResolveCalendarEventTypeColors(IReadOnlyDictionary<string, string>? overrides)
    {
        var items = new List<(string Type, string Color)>();
        foreach (var type in CalendarEventCatalog.GetTypes())
        {
            if (overrides is not null && overrides.TryGetValue(type, out var color) && !string.IsNullOrWhiteSpace(color))
                items.Add((type, color.Trim()));
            else
                items.Add((type, CalendarEventCatalog.DefaultColor));
        }

        return items;
    }

    public async Task<(bool Ok, IReadOnlyList<(string Type, string Color)>? Colors, string? ErrorCode)> UpdateCalendarEventTypeColorsAsync(
        Guid userId,
        IReadOnlyList<UpdateCalendarEventTypeColorItem>? items,
        CancellationToken ct = default)
    {
        var (ok, err) = await UpdateUserSettingsAsync(userId, null, null, items, ct);
        if (!ok)
            return (false, null, err);

        var user = await _db.Users.FirstOrDefaultAsync(u => u.Id == userId, ct);
        if (user is null)
            return (false, null, "unauthorized");

        return (true, GetResolvedCalendarEventTypeColors(user).ToList(), null);
    }

    public async Task<User> GetOrCreateByEmailAsync(string email, CancellationToken ct = default)
    {
        email = email.Trim().ToLowerInvariant();

        var user = await _db.Users.SingleOrDefaultAsync(x => x.Email == email, ct);
        if (user is not null) return user;

        user = new User { Id = UserId.StableUserId(email), Email = email };
        _db.Users.Add(user);

        try
        {
            await _db.SaveChangesAsync(ct);
            return user;
        }
        catch (DbUpdateException)
        {
            var existing = await _db.Users.SingleAsync(x => x.Email == email, ct);
            return existing;
        }
    }
}
