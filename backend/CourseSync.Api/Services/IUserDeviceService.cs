using CourseSync.Api.Data;

namespace CourseSync.Api.Services;

public interface IUserDeviceService
{
    Task RegisterDeviceAsync(Guid userId, DevicePlatform platform, string token, CancellationToken ct);
    Task UnregisterDeviceAsync(Guid userId, string token, CancellationToken ct);
}
