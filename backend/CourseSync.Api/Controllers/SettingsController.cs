using System.Security.Claims;
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
public sealed class SettingsController : ControllerBase
{
    private readonly UserService _users;

    public SettingsController(UserService users) => _users = users;

    [HttpGet]
    [ProducesResponseType(typeof(UserSettingsResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    public async Task<ActionResult<UserSettingsResponse>> Get(CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _users.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var colors = _users.GetResolvedCalendarEventTypeColors(user)
            .Select(x => new CalendarEventTypeColorItem(x.Type, x.Color))
            .ToList();

        return Ok(new UserSettingsResponse(user.NotificationsOn, user.DarkThemeOn, colors));
    }

    [HttpPut]
    [ProducesResponseType(StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    public async Task<IActionResult> Update([FromBody] UpdateUserSettingsRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var (ok, errorCode) = await _users.UpdateUserSettingsAsync(
            userId.Value,
            req.NotificationsOn,
            req.DarkThemeOn,
            req.CalendarEventTypeColors,
            ct);

        if (!ok)
        {
            if (errorCode == "unauthorized")
                return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));
            return BadRequest(new ErrorEnvelope(new ApiError(errorCode!)));
        }

        return Ok();
    }

    private Guid? GetCurrentUserId()
    {
        var sub = User.FindFirst(ClaimTypes.NameIdentifier)?.Value
                  ?? User.FindFirst("sub")?.Value;
        return Guid.TryParse(sub, out var id) ? id : null;
    }
}
