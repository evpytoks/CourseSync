using System.Security.Claims;
using System.Collections.Generic;
using CourseSync.Api.Data;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

public abstract class AuthorizedControllerBase : ControllerBase
{
    protected readonly record struct ErrorSpec(int StatusCode, string ApiCode);

    protected ActionResult ErrorResponse(string apiCode, int statusCode = 400)
        => StatusCode(statusCode, new ErrorEnvelope(new ApiError(apiCode)));

    protected ActionResult MapError(
        string? errorCode,
        IReadOnlyDictionary<string, ErrorSpec> knownErrors,
        string? fallbackApiCode = null,
        int fallbackStatusCode = 400)
    {
        if (!string.IsNullOrWhiteSpace(errorCode) && knownErrors.TryGetValue(errorCode, out var spec))
            return ErrorResponse(spec.ApiCode, spec.StatusCode);

        var code = fallbackApiCode ?? errorCode ?? "invalid_request";
        return ErrorResponse(code, fallbackStatusCode);
    }

    protected (Guid UserId, ActionResult? Error) ResolveCurrentUserIdOrUnauthorized()
    {
        var sub = User.FindFirst(ClaimTypes.NameIdentifier)?.Value
                  ?? User.FindFirst("sub")?.Value;
        if (!Guid.TryParse(sub, out var userId))
            return (default, ErrorResponse("unauthorized", 401));

        return (userId, null);
    }

    protected async Task<(Guid UserId, User? User, ActionResult? Error)> ResolveUserOrUnauthorizedAsync(
        IUserService userService,
        CancellationToken ct)
    {
        var (userId, idError) = ResolveCurrentUserIdOrUnauthorized();
        if (idError is not null)
            return (default, null, idError);

        var user = await userService.FindByIdAsync(userId, ct);
        if (user is null)
            return (default, null, ErrorResponse("unauthorized", 401));

        return (userId, user, null);
    }

    protected (Guid GroupId, ActionResult? Error) ResolveSelectedGroupOrError(User user)
    {
        if (user.CurrentGroupId is null)
            return (default, ErrorResponse("no_group_selected"));

        return (user.CurrentGroupId.Value, null);
    }
}
