using System.Security.Cryptography;
using System.Text;
using CourseSync.Api.Data;
using CourseSync.Api.Infrastructure;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace CourseSync.Api.Services;

public sealed class AuthLoginCodeService
{
    private readonly AppDbContext _db;
    private readonly ILogger<AuthLoginCodeService> _log;
    private readonly byte[] _hashKey;

    public AuthLoginCodeService(AppDbContext db, IOptions<AuthCodeOptions> opt, ILogger<AuthLoginCodeService> log)
    {
        _db = db;
        _log = log;

        var hashKey = (opt.Value.HashKey ?? "").Trim();
        if (string.IsNullOrWhiteSpace(hashKey))
            throw new InvalidOperationException("AuthCode:HashKey is missing");

        _hashKey = Encoding.UTF8.GetBytes(hashKey);
    }

    public async Task<(string requestId, int expiresInSec, string plainCode)> CreateAsync(User user, int ttlSeconds, CancellationToken ct)
    {
        var now = DateTimeOffset.UtcNow;
        await CleanupAsync(now, ct);

        var email = NormalizeEmail(user.Email);

        await _db.AuthLoginRequests
            .Where(x => x.UserId == user.Id && x.UsedAt == null && x.ExpiresAt > now)
            .ExecuteDeleteAsync(ct);

        var requestId = "req_" + Base64Url(RandomNumberGenerator.GetBytes(16));
        var code = RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6");

        var rec = new AuthLoginRequest
        {
            RequestId = requestId,
            UserId = user.Id,
            Email = email,
            CodeHash = Hash(email, requestId, code),
            CreatedAt = now,
            ExpiresAt = now.AddSeconds(ttlSeconds),
            AttemptCount = 0,
            UsedAt = null
        };

        _db.AuthLoginRequests.Add(rec);
        await _db.SaveChangesAsync(ct);

        return (requestId, ttlSeconds, code);
    }

    public async Task InvalidateAsync(string requestId, CancellationToken ct)
    {
        requestId = (requestId ?? "").Trim();
        if (string.IsNullOrWhiteSpace(requestId)) return;

        await _db.AuthLoginRequests
            .Where(x => x.RequestId == requestId)
            .ExecuteDeleteAsync(ct);
    }

    public async Task<VerifyResult> VerifyAsync(string email, string requestId, string code, int maxAttempts, CancellationToken ct)
    {
        var now = DateTimeOffset.UtcNow;
        await CleanupAsync(now, ct);

        var emailNorm = NormalizeEmail(email);
        requestId = (requestId ?? "").Trim();
        code = (code ?? "").Trim();

        if (string.IsNullOrWhiteSpace(emailNorm) || string.IsNullOrWhiteSpace(requestId) || string.IsNullOrWhiteSpace(code))
            return VerifyResult.Invalid;

        var rec = await _db.AuthLoginRequests.SingleOrDefaultAsync(x => x.RequestId == requestId, ct);
        if (rec is null) return VerifyResult.Invalid;

        if (!string.Equals(rec.Email, emailNorm, StringComparison.OrdinalIgnoreCase)) return VerifyResult.Invalid;
        if (rec.UsedAt is not null || rec.ExpiresAt <= now) return VerifyResult.Invalid;

        if (rec.AttemptCount >= maxAttempts) return VerifyResult.TooManyAttempts;

        rec.AttemptCount++;

        var ok = CryptographicOperations.FixedTimeEquals(rec.CodeHash, Hash(emailNorm, requestId, code));

        if (!ok)
        {
            try
            {
                await _db.SaveChangesAsync(ct);
            }
            catch (DbUpdateConcurrencyException)
            {
                return VerifyResult.Invalid;
            }

            return VerifyResult.Invalid;
        }

        try
        {
            rec.UsedAt = now;
            await _db.SaveChangesAsync(ct);
        }
        catch (DbUpdateConcurrencyException)
        {
            return VerifyResult.Invalid;
        }

        return VerifyResult.Ok;
    }

    private async Task CleanupAsync(DateTimeOffset now, CancellationToken ct)
    {
        await _db.AuthLoginRequests
            .Where(x => x.UsedAt != null || x.ExpiresAt <= now)
            .ExecuteDeleteAsync(ct);
    }

    private byte[] Hash(string email, string requestId, string code)
    {
        var payload = $"{NormalizeEmail(email)}:{requestId}:{code}";
        return HMACSHA256.HashData(_hashKey, Encoding.UTF8.GetBytes(payload));
    }

    private static string NormalizeEmail(string email) => (email ?? "").Trim().ToLowerInvariant();

    private static string Base64Url(byte[] bytes)
    {
        var s = Convert.ToBase64String(bytes);
        s = s.TrimEnd('=').Replace('+', '-').Replace('/', '_');
        return s;
    }
}
