using System.Collections.Concurrent;
using System.Security.Cryptography;
using System.Text;

namespace CourseSync.Api.Services;

public sealed class AuthCodeStore
{
    private sealed class Record
    {
        public required string Email { get; init; }
        public required string CodeHash { get; init; }
        public required DateTimeOffset ExpiresAt { get; init; }
        public int Attempts;
        public bool Used;
    }

    private readonly ConcurrentDictionary<string, Record> _store = new();

    public (string requestId, int expiresInSec, string plainCode) CreateCode(string email, int ttlSeconds)
    {
        CleanupExpired();

        var requestId = "req_" + Guid.NewGuid().ToString("N")[..10];
        var code = RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6");

        _store[requestId] = new Record
        {
            Email = email,
            CodeHash = Sha256(code),
            ExpiresAt = DateTimeOffset.UtcNow.AddSeconds(ttlSeconds),
            Attempts = 0,
            Used = false
        };

        return (requestId, ttlSeconds, code);
    }

    public VerifyResult VerifyCode(string email, string requestId, string code, int maxAttempts)
    {
        CleanupExpired();

        if (!_store.TryGetValue(requestId, out var rec)) return VerifyResult.Invalid;
        if (!string.Equals(rec.Email, email, StringComparison.OrdinalIgnoreCase)) return VerifyResult.Invalid;
        if (rec.Used || rec.ExpiresAt <= DateTimeOffset.UtcNow) return VerifyResult.Invalid;

        if (rec.Attempts >= maxAttempts) return VerifyResult.TooManyAttempts;

        rec.Attempts++;

        if (rec.CodeHash != Sha256(code)) return VerifyResult.Invalid;

        rec.Used = true;
        return VerifyResult.Ok;
    }

    private void CleanupExpired()
    {
        var now = DateTimeOffset.UtcNow;
        foreach (var (key, value) in _store)
        {
            if (value.Used || value.ExpiresAt <= now)
                _store.TryRemove(key, out _);
        }
    }

    private static string Sha256(string value)
    {
        var bytes = SHA256.HashData(Encoding.UTF8.GetBytes(value));
        return Convert.ToHexString(bytes);
    }
}

public enum VerifyResult
{
    Ok,
    Invalid,
    TooManyAttempts
}
