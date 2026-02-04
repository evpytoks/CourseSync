using System.Security.Cryptography;
using System.Text;

namespace CourseSync.Api.Services;

public static class UserId
{
    public static Guid StableUserId(string email)
    {
        email = email.Trim().ToLowerInvariant();

        var bytes = SHA256.HashData(Encoding.UTF8.GetBytes(email));
        Span<byte> g = stackalloc byte[16];
        bytes.AsSpan(0, 16).CopyTo(g);

        return new Guid(g);
    }
}
