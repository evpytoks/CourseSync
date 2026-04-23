using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

[ApiController]
[Route("settings")]
[Authorize]
[Produces("application/json")]
public sealed class SettingsController : AuthorizedControllerBase
{
    private readonly IUserService _users;

    public SettingsController(IUserService users) => _users = users;

    [HttpGet]
    [ProducesResponseType(typeof(UserSettingsResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    public async Task<ActionResult<UserSettingsResponse>> Get(CancellationToken ct)
    {
        var (userId, user, authError) = await ResolveUserOrUnauthorizedAsync(_users, ct);
        if (authError is not null)
            return authError;

        var colors = _users.GetResolvedCalendarEventTypeColors(user!)
            .Select(x => new CalendarEventTypeColorItem(x.Type, x.Color))
            .ToList();

        return Ok(new UserSettingsResponse(user!.NotificationsOn, user.DarkThemeOn, colors));
    }

    [HttpPut]
    [ProducesResponseType(StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    public async Task<IActionResult> Update([FromBody] UpdateUserSettingsRequest req, CancellationToken ct)
    {
        var (userId, authError) = ResolveCurrentUserIdOrUnauthorized();
        if (authError is not null)
            return authError;

        if (req is null)
            return ErrorResponse("invalid_request");

        var (ok, errorCode) = await _users.UpdateUserSettingsAsync(
            userId,
            req.NotificationsOn,
            req.DarkThemeOn,
            req.CalendarEventTypeColors,
            ct);

        if (!ok)
            return errorCode == "unauthorized"
                ? ErrorResponse("unauthorized", 401)
                : ErrorResponse(errorCode!);

        return Ok();
    }

}
