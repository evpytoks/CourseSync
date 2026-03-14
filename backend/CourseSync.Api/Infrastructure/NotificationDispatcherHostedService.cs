using CourseSync.Api.Data;
using CourseSync.Api.Infrastructure.Push;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Infrastructure;

public sealed class NotificationDispatcherHostedService : BackgroundService
{
    private static readonly TimeSpan Interval = TimeSpan.FromSeconds(10);
    private const int BatchSize = 100;
    private const int MaxAttempts = 5;

    private readonly IServiceScopeFactory _scopeFactory;
    private readonly ILogger<NotificationDispatcherHostedService> _log;

    public NotificationDispatcherHostedService(IServiceScopeFactory scopeFactory, ILogger<NotificationDispatcherHostedService> log)
    {
        _scopeFactory = scopeFactory;
        _log = log;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                using var scope = _scopeFactory.CreateScope();
                var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
                var pushSender = scope.ServiceProvider.GetRequiredService<IPushSender>();

                var pending = await db.Notifications
                    .Where(n => n.SentAt == null && n.SendAttempts < MaxAttempts)
                    .OrderBy(n => n.CreatedAt)
                    .Take(BatchSize)
                    .ToListAsync(stoppingToken);

                if (pending.Count > 0)
                {
                    var devices = await db.UserDevices
                        .Where(d => d.IsActive && pending.Select(n => n.UserId).Contains(d.UserId))
                        .ToListAsync(stoppingToken);

                    foreach (var notification in pending)
                    {
                        var userDevices = devices.Where(d => d.UserId == notification.UserId).ToList();
                        if (userDevices.Count == 0)
                        {
                            notification.SendAttempts++;
                            continue;
                        }

                        foreach (var device in userDevices)
                        {
                            try
                            {
                                await pushSender.SendAsync(device, notification, stoppingToken);
                            }
                            catch (Exception ex)
                            {
                                _log.LogError(ex, "Failed to send push to device {DeviceId} for notification {NotificationId}", device.Id, notification.Id);
                                notification.SendAttempts++;
                            }
                        }

                        if (notification.SendAttempts < MaxAttempts)
                        {
                            notification.SentAt = DateTimeOffset.UtcNow;
                        }
                    }

                    await db.SaveChangesAsync(stoppingToken);
                }
            }
            catch (Exception ex)
            {
                _log.LogError(ex, "Notification dispatch failed");
            }

            try
            {
                await Task.Delay(Interval, stoppingToken);
            }
            catch (OperationCanceledException)
            {
                return;
            }
        }
    }
}

