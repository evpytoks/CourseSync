using CourseSync.Api.Data;

namespace CourseSync.Api.Infrastructure.Push;

public interface IPushSender
{
    Task SendAsync(UserDevice device, Notification notification, CancellationToken ct);
}

