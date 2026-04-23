using CourseSync.Api.Data;

namespace CourseSync.Api.Services;

public interface IAuthLoginCodeService
{
    Task<(CreateAuthCodeStatus status, string requestId, DateTimeOffset expiresAt, string plainCode)> CreateAsync(
        User user,
        int ttlSeconds,
        int cooldownSeconds,
        string? plainCodeOverride,
        CancellationToken ct);
    Task MarkCodeSentAsync(Guid userId, CancellationToken ct);
    Task InvalidateAsync(string requestId, CancellationToken ct);
    Task<VerifyResult> VerifyAsync(string email, string requestId, string code, int maxAttempts, CancellationToken ct);
}
