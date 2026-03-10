using System.Security.Claims;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Authorization;
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
    public async Task<ActionResult<UserSettingsResponse>> Get(CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _users.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        return Ok(new UserSettingsResponse(user.NotificationsOn, user.DarkThemeOn));
    }

    [HttpPut]
    public async Task<IActionResult> Update([FromBody] UpdateUserSettingsRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        await _users.UpdateSettingsAsync(userId.Value, req.NotificationsOn, req.DarkThemeOn, ct);
        return Ok();
    }

    private Guid? GetCurrentUserId()
    {
        var sub = User.FindFirst(ClaimTypes.NameIdentifier)?.Value
                  ?? User.FindFirst("sub")?.Value;
        return Guid.TryParse(sub, out var id) ? id : null;
    }
}

