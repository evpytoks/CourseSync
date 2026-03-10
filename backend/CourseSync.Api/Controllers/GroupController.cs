using System.Security.Claims;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

[ApiController]
[Route("group")]
[Authorize]
[Produces("application/json")]
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

    [HttpPost("join")]
    public async Task<ActionResult<GroupJoinResponse>> Join([FromBody] GroupJoinRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var codeValidation = GroupService.ValidateGroupCode(req.Code);
        if (!codeValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(codeValidation.ErrorCode!)));

        var result = await _groupService.JoinByCodeAsync(userId.Value, (req.Code ?? "").Trim(), ct);
        if (result is null)
            return NotFound(new ErrorEnvelope(new ApiError("group_not_found")));

        return Ok(new GroupJoinResponse(result.Value.GroupId, result.Value.Role));
    }

    [HttpPut("{id:guid}/change")]
    public async Task<ActionResult<GroupChangeResponse>> Change(Guid id, [FromBody] GroupChangeRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var (ok, errorCode) = await _groupService.ChangeNameAsync(userId.Value, id, req.Name ?? "", ct);
        if (!ok)
        {
            if (errorCode is "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            return BadRequest(new ErrorEnvelope(new ApiError(errorCode!)));
        }

        return Ok(new GroupChangeResponse(id, req.Name!.Trim()));
    }

    [HttpPost("{id:guid}/choose")]
    public async Task<ActionResult<ChooseGroupResponse>> Choose(Guid id, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var (ok, groupId, name) = await _groupService.ChooseGroupAsync(userId.Value, id, ct);
        if (!ok)
            return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));

        return Ok(new ChooseGroupResponse(groupId!.Value, name!));
    }

    [HttpGet("{id:guid}/name")]
    public async Task<ActionResult<GroupNameResponse>> GetName(Guid id, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var (ok, name) = await _groupService.GetGroupNameAsync(userId.Value, id, ct);
        if (!ok)
            return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));

        return Ok(new GroupNameResponse(id, name!));
    }

    private Guid? GetCurrentUserId()
    {
        var sub = User.FindFirst(ClaimTypes.NameIdentifier)?.Value
                  ?? User.FindFirst("sub")?.Value;
        return Guid.TryParse(sub, out var id) ? id : null;
    }
}
