namespace CourseSync.Api.Services;

public interface IRefreshTokenService
{
    Task<(string refreshToken, DateTimeOffset expiresAt, int tokenVersion)> EstablishSingleSessionAsync(Guid userId, CancellationToken ct);
    Task<(RefreshRotateStatus status, Guid userId, string newRefreshToken, DateTimeOffset newRefreshExpiresAt)> RotateAsync(string refreshToken, CancellationToken ct);
    Task RevokeAsync(string refreshToken, CancellationToken ct);
}
