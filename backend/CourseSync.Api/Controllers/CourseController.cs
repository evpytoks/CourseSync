using System.Security.Claims;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

[ApiController]
[Route("course")]
[Authorize]
[Produces("application/json")]
public sealed class CourseController : ControllerBase
{
    private readonly CourseService _courseService;
    private readonly UserService _userService;

    public CourseController(CourseService courseService, UserService userService)
    {
        _courseService = courseService;
        _userService = userService;
    }

    [HttpGet("list")]
    public async Task<ActionResult<CourseListResponse>> List(CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var dtos = await _courseService.GetByGroupIdAsync(user.CurrentGroupId.Value, ct);
        var items = dtos.Select(d => new CourseListItem(d.Id, d.Name, d.GeneralInfo, d.UsefulLinks)).ToList();
        return Ok(new CourseListResponse(items));
    }

    private Guid? GetCurrentUserId()
    {
        var sub = User.FindFirst(ClaimTypes.NameIdentifier)?.Value
                  ?? User.FindFirst("sub")?.Value;
        return Guid.TryParse(sub, out var id) ? id : null;
    }
}
