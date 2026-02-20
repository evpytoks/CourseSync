using CourseSync.Api.Data;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Infrastructure;

public sealed class TokenVersionJwtBearerEvents : JwtBearerEvents
{
    private readonly AppDbContext _db;
    public TokenVersionJwtBearerEvents(AppDbContext db) => _db = db;

    public override async Task TokenValidated(TokenValidatedContext context)
    {
        var uidStr = context.Principal?.FindFirst("uid")?.Value
                     ?? context.Principal?.FindFirst("sub")?.Value;
        var tvStr = context.Principal?.FindFirst("tv")?.Value;

        if (!Guid.TryParse(uidStr, out var userId) || !int.TryParse(tvStr, out var tokenVersion))
        {
            context.Fail("missing_uid_or_tv");
            return;
        }

        var dbVersion = await _db.Users
            .Where(x => x.Id == userId)
            .Select(x => x.TokenVersion)
            .SingleOrDefaultAsync(context.HttpContext.RequestAborted);

        if (dbVersion != tokenVersion)
        {
            context.Fail("token_version_mismatch");
            return;
        }
    }
}

