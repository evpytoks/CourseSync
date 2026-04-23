using CourseSync.Api.Data;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

[ApiController]
[Route("devices")]
[Authorize]
[Produces("application/json")]
public sealed class DeviceController : AuthorizedControllerBase
{
    private readonly IUserDeviceService _devices;

    public DeviceController(IUserDeviceService devices) => _devices = devices;

    [HttpPost]
    public async Task<IActionResult> Register([FromBody] RegisterDeviceRequest req, CancellationToken ct)
    {
        var (userId, authError) = ResolveCurrentUserIdOrUnauthorized();
        if (authError is not null)
            return authError;

        if (req is null)
            return ErrorResponse("device_token_required");

        if (string.IsNullOrWhiteSpace(req.Token))
            return ErrorResponse("device_token_required");

        var platform = req.Platform?.ToLowerInvariant() switch
        {
            "ios" => DevicePlatform.Ios,
            "android" => DevicePlatform.Android,
            _ => DevicePlatform.Unknown
        };

        if (platform == DevicePlatform.Unknown)
            return ErrorResponse("device_platform_invalid");

        await _devices.RegisterDeviceAsync(userId, platform, req.Token, ct);
        return Ok();
    }

    [HttpDelete]
    public async Task<IActionResult> Unregister([FromBody] UnregisterDeviceRequest req, CancellationToken ct)
    {
        var (userId, authError) = ResolveCurrentUserIdOrUnauthorized();
        if (authError is not null)
            return authError;

        if (req is null)
            return ErrorResponse("device_token_required");

        if (string.IsNullOrWhiteSpace(req.Token))
            return ErrorResponse("device_token_required");

        await _devices.UnregisterDeviceAsync(userId, req.Token, ct);
        return Ok();
    }
}
