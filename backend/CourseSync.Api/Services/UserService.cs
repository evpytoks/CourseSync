using CourseSync.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Services;

public sealed class UserService
{
    private readonly AppDbContext _db;
    public UserService(AppDbContext db) => _db = db;

    public async Task<User?> FindByEmailAsync(string email, CancellationToken ct = default)
    {
        email = email.Trim().ToLowerInvariant();
        return await _db.Users.SingleOrDefaultAsync(x => x.Email == email, ct);
    }

    public async Task<User?> FindByIdAsync(Guid id, CancellationToken ct = default)
        => await _db.Users.SingleOrDefaultAsync(x => x.Id == id, ct);

    public async Task ClearCurrentGroupAsync(Guid userId, CancellationToken ct = default)
    {
        await _db.Users
            .Where(u => u.Id == userId)
            .ExecuteUpdateAsync(s => s.SetProperty(u => u.CurrentGroupId, (Guid?)null), ct);
    }

    public async Task<User> GetOrCreateByEmailAsync(string email, CancellationToken ct = default)
    {
        email = email.Trim().ToLowerInvariant();

        var user = await _db.Users.SingleOrDefaultAsync(x => x.Email == email, ct);
        if (user is not null) return user;

        user = new User { Id = UserId.StableUserId(email), Email = email };
        _db.Users.Add(user);

        try
        {
            await _db.SaveChangesAsync(ct);
            return user;
        }
        catch (DbUpdateException)
        {
            var existing = await _db.Users.SingleAsync(x => x.Email == email, ct);
            return existing;
        }
    }
}
