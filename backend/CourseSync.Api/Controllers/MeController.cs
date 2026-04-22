using System.Security.Claims;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

[ApiController]
[Route("me")]
[Authorize]
[Produces("application/json")]
public sealed class MeController : ControllerBase
{
    private readonly GroupService _groupService;

    public MeController(GroupService groupService) => _groupService = groupService;

    [HttpGet("current-group")]
    public async Task<ActionResult<GroupDetailsResponse>> GetCurrentGroup(CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var (ok, groupId, name, role, groupCode, errorCode) = await _groupService.GetGroupDetailsAsync(userId.Value, ct);
        if (!ok)
        {
            if (errorCode == "no_group_selected")
                return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));
            return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
        }

        return Ok(new GroupDetailsResponse(groupId, name, role, groupCode));
    }

    [HttpPut("current-group")]
    public async Task<IActionResult> SetCurrentGroup([FromBody] SetCurrentGroupRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (req is null)
            return BadRequest(new ErrorEnvelope(new ApiError("group_id_required")));

        if (req.GroupId == Guid.Empty)
            return BadRequest(new ErrorEnvelope(new ApiError("group_id_required")));

        var (ok, _, _) = await _groupService.ChooseGroupAsync(userId.Value, req.GroupId, ct);
        if (!ok)
            return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));

        return Ok();
    }

    private Guid? GetCurrentUserId()
    {
        var sub = User.FindFirst(ClaimTypes.NameIdentifier)?.Value
                  ?? User.FindFirst("sub")?.Value;
        return Guid.TryParse(sub, out var id) ? id : null;
    }
}
