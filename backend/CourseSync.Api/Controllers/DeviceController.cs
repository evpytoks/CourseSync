using System.Security.Claims;
using CourseSync.Api.Data;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

[ApiController]
[Route("device")]
[Authorize]
[Produces("application/json")]
public sealed class DeviceController : ControllerBase
{
    private readonly UserDeviceService _devices;

    public DeviceController(UserDeviceService devices) => _devices = devices;

    [HttpPost("register")]
    public async Task<IActionResult> Register([FromBody] RegisterDeviceRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (string.IsNullOrWhiteSpace(req.Token))
            return BadRequest(new ErrorEnvelope(new ApiError("device_token_required")));

        var platform = req.Platform?.ToLowerInvariant() switch
        {
            "ios" => DevicePlatform.Ios,
            "android" => DevicePlatform.Android,
            _ => DevicePlatform.Unknown
        };

        if (platform == DevicePlatform.Unknown)
            return BadRequest(new ErrorEnvelope(new ApiError("device_platform_invalid")));

        await _devices.RegisterDeviceAsync(userId.Value, platform, req.Token, ct);
        return Ok();
    }

    [HttpPost("unregister")]
    public async Task<IActionResult> Unregister([FromBody] UnregisterDeviceRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (string.IsNullOrWhiteSpace(req.Token))
            return BadRequest(new ErrorEnvelope(new ApiError("device_token_required")));

        await _devices.UnregisterDeviceAsync(userId.Value, req.Token, ct);
        return Ok();
    }

    private Guid? GetCurrentUserId()
    {
        var sub = User.FindFirst(ClaimTypes.NameIdentifier)?.Value
                  ?? User.FindFirst("sub")?.Value;
        return Guid.TryParse(sub, out var id) ? id : null;
    }
}

