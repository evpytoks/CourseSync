using System.Security.Cryptography;
using System.Text;
using CourseSync.Api.Data;
using CourseSync.Api.Infrastructure;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Storage;
using Microsoft.Extensions.Options;

namespace CourseSync.Api.Services;

public enum RefreshRotateStatus
{
    Ok,
    Invalid,
    RevokedOrExpired,
    Reused
}

public sealed class RefreshTokenService
{
    private readonly AppDbContext _db;
    private readonly AuthTokensOptions _opt;
    private readonly byte[] _hashKey;

    public RefreshTokenService(AppDbContext db, IOptions<AuthTokensOptions> opt)
    {
        _db = db;
        _opt = opt.Value;
        _hashKey = Encoding.UTF8.GetBytes((_opt.RefreshTokenHashKey ?? "").Trim());
    }

    public async Task<(string refreshToken, DateTimeOffset expiresAt, int tokenVersion)> EstablishSingleSessionAsync(Guid userId, CancellationToken ct)
    {
        var now = DateTimeOffset.UtcNow;

        await using var tx = await TryBeginTransactionAsync(ct);

        await RevokeAllActiveForUserAsync(userId, now, ct);
        var tokenVersion = await BumpUserTokenVersionAsync(userId, ct);

        var (rec, plain) = CreateRecord(userId, now);
        _db.RefreshTokens.Add(rec);
        await _db.SaveChangesAsync(ct);

        if (tx is not null)
            await tx.CommitAsync(ct);

        return (plain, rec.ExpiresAt, tokenVersion);
    }

    public async Task<(RefreshRotateStatus status, Guid userId, string newRefreshToken, DateTimeOffset newRefreshExpiresAt)> RotateAsync(string refreshToken, CancellationToken ct)
    {
        refreshToken = (refreshToken ?? "").Trim();
        if (string.IsNullOrWhiteSpace(refreshToken))
            return (RefreshRotateStatus.Invalid, Guid.Empty, "", default);

        var now = DateTimeOffset.UtcNow;
        var tokenHash = Hash(refreshToken);

        await using var tx = await TryBeginTransactionAsync(ct);

        await LockRefreshTokenRowAsync(tokenHash, ct);

        RefreshToken? rec;
        if (_db.Database.IsRelational())
        {
            rec = await _db.RefreshTokens.SingleOrDefaultAsync(x => x.TokenHash == tokenHash, ct);
        }
        else
        {
            var rows = await _db.RefreshTokens.ToListAsync(ct);
            rec = rows.SingleOrDefault(x => x.TokenHash.AsSpan().SequenceEqual(tokenHash));
        }
        if (rec is null) return (RefreshRotateStatus.Invalid, Guid.Empty, "", default);

        if (rec.ExpiresAt <= now || rec.RevokedAt is not null)
            return (RefreshRotateStatus.RevokedOrExpired, Guid.Empty, "", default);

        if (rec.UsedAt is not null)
        {
            await RevokeAllActiveForUserAsync(rec.UserId, now, ct);
            await BumpUserTokenVersionAsync(rec.UserId, ct);
            if (tx is not null)
                await tx.CommitAsync(ct);
            return (RefreshRotateStatus.Reused, Guid.Empty, "", default);
        }

        var (newRec, newPlain) = CreateRecord(rec.UserId, now);
        _db.RefreshTokens.Add(newRec);

        rec.UsedAt = now;
        rec.ReplacedByTokenId = newRec.Id;

        await _db.SaveChangesAsync(ct);
        if (tx is not null)
            await tx.CommitAsync(ct);

        return (RefreshRotateStatus.Ok, rec.UserId, newPlain, newRec.ExpiresAt);
    }

    public async Task RevokeAsync(string refreshToken, CancellationToken ct)
    {
        refreshToken = (refreshToken ?? "").Trim();
        if (string.IsNullOrWhiteSpace(refreshToken)) return;

        var now = DateTimeOffset.UtcNow;
        var tokenHash = Hash(refreshToken);

        RefreshToken? rec;
        if (_db.Database.IsRelational())
        {
            rec = await _db.RefreshTokens.SingleOrDefaultAsync(x => x.TokenHash == tokenHash, ct);
        }
        else
        {
            var rows = await _db.RefreshTokens.ToListAsync(ct);
            rec = rows.SingleOrDefault(x => x.TokenHash.AsSpan().SequenceEqual(tokenHash));
        }
        if (rec is null) return;

        rec.RevokedAt = now;
        await _db.SaveChangesAsync(ct);
    }

    private async Task RevokeAllActiveForUserAsync(Guid userId, DateTimeOffset now, CancellationToken ct)
    {
        if (_db.Database.IsRelational())
        {
            await _db.RefreshTokens
                .Where(x => x.UserId == userId && x.UsedAt == null && x.RevokedAt == null && x.ExpiresAt > now)
                .ExecuteUpdateAsync(s => s.SetProperty(x => x.RevokedAt, now), ct);
            return;
        }

        var rows = await _db.RefreshTokens
            .Where(x => x.UserId == userId && x.UsedAt == null && x.RevokedAt == null && x.ExpiresAt > now)
            .ToListAsync(ct);

        if (rows.Count == 0) return;

        foreach (var rec in rows)
            rec.RevokedAt = now;

        await _db.SaveChangesAsync(ct);
    }

    private async Task<int> BumpUserTokenVersionAsync(Guid userId, CancellationToken ct)
    {
        await LockUserRowAsync(userId, ct);

        var user = await _db.Users.SingleOrDefaultAsync(x => x.Id == userId, ct);
        if (user is null) return 0;

        user.TokenVersion++;
        await _db.SaveChangesAsync(ct);
        return user.TokenVersion;
    }

    private async Task LockUserRowAsync(Guid userId, CancellationToken ct)
    {
        if (!_db.Database.IsRelational())
            return;

        await _db.Database.ExecuteSqlInterpolatedAsync(
            $"SELECT 1 FROM users WHERE \"Id\" = {userId} FOR UPDATE",
            ct);
    }

    private async Task LockRefreshTokenRowAsync(byte[] tokenHash, CancellationToken ct)
    {
        if (!_db.Database.IsRelational())
            return;

        await _db.Database.ExecuteSqlInterpolatedAsync(
            $"SELECT 1 FROM refresh_tokens WHERE token_hash = {tokenHash} FOR UPDATE",
            ct);
    }

    private byte[] Hash(string refreshToken)
        => HMACSHA256.HashData(_hashKey, Encoding.UTF8.GetBytes(refreshToken));

    private async Task<IDbContextTransaction?> TryBeginTransactionAsync(CancellationToken ct)
    {
        try
        {
            return await _db.Database.BeginTransactionAsync(ct);
        }
        catch (InvalidOperationException)
        {
            return null;
        }
        catch (NotSupportedException)
        {
            return null;
        }
    }

    private (RefreshToken rec, string plain) CreateRecord(Guid userId, DateTimeOffset now)
    {
        var expiresAt = now.AddDays(_opt.RefreshTokenTtlDays);

        var plain = Base64Url(RandomNumberGenerator.GetBytes(32));
        var hash = Hash(plain);

        var rec = new RefreshToken
        {
            Id = Guid.NewGuid(),
            UserId = userId,
            TokenHash = hash,
            CreatedAt = now,
            ExpiresAt = expiresAt
        };

        return (rec, plain);
    }

    private static string Base64Url(byte[] bytes)
    {
        var s = Convert.ToBase64String(bytes);
        s = s.TrimEnd('=').Replace('+', '-').Replace('/', '_');
        return s;
    }
}
