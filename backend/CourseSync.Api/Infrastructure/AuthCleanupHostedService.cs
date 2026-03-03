using CourseSync.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Infrastructure;

public sealed class AuthCleanupHostedService : BackgroundService
{
    private static readonly TimeSpan Interval = TimeSpan.FromMinutes(30);

    private readonly IServiceScopeFactory _scopeFactory;
    private readonly ILogger<AuthCleanupHostedService> _log;

    public AuthCleanupHostedService(IServiceScopeFactory scopeFactory, ILogger<AuthCleanupHostedService> log)
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

                var now = DateTimeOffset.UtcNow;

                await db.AuthLoginRequests
                    .Where(x => x.UsedAt != null || x.ExpiresAt <= now)
                    .ExecuteDeleteAsync(stoppingToken);

                await db.RefreshTokens
                    .Where(x => x.ExpiresAt <= now)
                    .ExecuteDeleteAsync(stoppingToken);
            }
            catch (Exception ex)
            {
                _log.LogError(ex, "Auth cleanup failed");
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

