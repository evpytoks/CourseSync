using CourseSync.Api.Data;

namespace CourseSync.Api.Infrastructure.Push;

public sealed class NoopPushSender : IPushSender
{
    private readonly ILogger<NoopPushSender> _log;

    public NoopPushSender(ILogger<NoopPushSender> log) => _log = log;

    public Task SendAsync(UserDevice device, Notification notification, CancellationToken ct)
    {
        _log.LogInformation("Pretend push to {UserId} on {Platform}: {Title}", device.UserId, device.Platform, notification.Title);
        return Task.CompletedTask;
    }
}

