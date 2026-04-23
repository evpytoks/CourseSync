using CourseSync.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Services;

public sealed class UserDeviceService : IUserDeviceService
{
    private readonly AppDbContext _db;

    public UserDeviceService(AppDbContext db) => _db = db;

    public async Task RegisterDeviceAsync(Guid userId, DevicePlatform platform, string token, CancellationToken ct)
    {
        token = token.Trim();

        var existingByToken = await _db.UserDevices.FirstOrDefaultAsync(d => d.Token == token, ct);
        if (existingByToken is not null)
        {
            existingByToken.UserId = userId;
            existingByToken.Platform = platform;
            existingByToken.IsActive = true;
            existingByToken.LastUsedAt = DateTimeOffset.UtcNow;
            await _db.SaveChangesAsync(ct);
            return;
        }

        var device = new UserDevice
        {
            Id = Guid.NewGuid(),
            UserId = userId,
            Platform = platform,
            Token = token,
            CreatedAt = DateTimeOffset.UtcNow,
            LastUsedAt = DateTimeOffset.UtcNow,
            IsActive = true
        };

        _db.UserDevices.Add(device);
        await _db.SaveChangesAsync(ct);
    }

    public async Task UnregisterDeviceAsync(Guid userId, string token, CancellationToken ct)
    {
        token = token.Trim();
        var device = await _db.UserDevices.FirstOrDefaultAsync(d => d.UserId == userId && d.Token == token, ct);
        if (device is null) return;

        device.IsActive = false;
        device.LastUsedAt = DateTimeOffset.UtcNow;
        await _db.SaveChangesAsync(ct);
    }
}

