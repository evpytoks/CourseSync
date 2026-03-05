using System.Security.Claims;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

[ApiController]
[Route("group")]
[Authorize]
public sealed class GroupController : ControllerBase
{
    private readonly GroupService _groupService;

    public GroupController(GroupService groupService) => _groupService = groupService;

    [HttpPost("create")]
    public async Task<ActionResult<CreateGroupResponse>> Create([FromBody] CreateGroupRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var validation = GroupService.ValidateGroupName(req.Name);
        if (!validation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(validation.ErrorCode!)));

        var result = await _groupService.CreateGroupAsync(userId.Value, req.Name!.Trim(), ct);
        if (result is null)
            return StatusCode(500, new ErrorEnvelope(new ApiError("group_creation_failed")));

        return Ok(new CreateGroupResponse(result.Value.GroupId, result.Value.Name, result.Value.Code));
    }

    [HttpGet("list")]
    public async Task<ActionResult<GroupListResponse>> List(CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var dtos = await _groupService.GetUserGroupsAsync(userId.Value, ct);
        var items = dtos.Select(d => new GroupListItem(d.Id, d.Name, d.Role, d.GroupCode)).ToList();
        return Ok(new GroupListResponse(items));
    }

    private Guid? GetCurrentUserId()
    {
        var sub = User.FindFirst(ClaimTypes.NameIdentifier)?.Value
                  ?? User.FindFirst("sub")?.Value;
        return Guid.TryParse(sub, out var id) ? id : null;
    }
}
