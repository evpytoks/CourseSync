using System.Security.Claims;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

[ApiController]
[Route("groups")]
[Authorize]
[Produces("application/json")]
public sealed class GroupController : ControllerBase
{
    private readonly GroupService _groupService;

    public GroupController(GroupService groupService) => _groupService = groupService;

    [HttpGet]
    public async Task<ActionResult<GroupListResponse>> List(CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var dtos = await _groupService.GetUserGroupsAsync(userId.Value, ct);
        var items = dtos.Select(d => new GroupListItem(d.Id, d.Name, d.Role, d.GroupCode, d.CreatorEmail)).ToList();
        return Ok(new GroupListResponse(items));
    }

    [HttpGet("owned")]
    public async Task<ActionResult<OwnerGroupListResponse>> OwnerList(CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var dtos = await _groupService.GetOwnerGroupsAsync(userId.Value, ct);
        var items = dtos.Select(d => new OwnerGroupListItem(d.Id, d.Name)).ToList();
        return Ok(new OwnerGroupListResponse(items));
    }

    [HttpPost]
    public async Task<IActionResult> Create([FromBody] CreateGroupRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (req is null)
            return BadRequest(new ErrorEnvelope(new ApiError("group_name_required")));

        var validation = GroupService.ValidateGroupName(req.Name);
        if (!validation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(validation.ErrorCode!)));

        var result = await _groupService.CreateGroupAsync(userId.Value, req.Name!.Trim(), ct);
        if (result is null)
            return StatusCode(500, new ErrorEnvelope(new ApiError("group_creation_failed")));

        return Ok();
    }

    [HttpPost("join")]
    public async Task<IActionResult> Join([FromBody] GroupJoinRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (req is null)
            return BadRequest(new ErrorEnvelope(new ApiError("invalid_code_format")));

        var codeValidation = GroupService.ValidateGroupCode(req.Code);
        if (!codeValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(codeValidation.ErrorCode!)));

        var result = await _groupService.JoinByCodeAsync(userId.Value, (req.Code ?? "").Trim(), ct);
        if (!result.Ok)
        {
            if (result.ErrorCode == "group_join_blocked")
                return StatusCode(403, new ErrorEnvelope(new ApiError("group_join_blocked")));
            if (result.ErrorCode == "invalid_code_format")
                return BadRequest(new ErrorEnvelope(new ApiError("invalid_code_format")));
            return NotFound(new ErrorEnvelope(new ApiError("group_not_found")));
        }

        return Ok();
    }

    [HttpGet("{id:guid}/participants")]
    [ProducesResponseType(typeof(GroupParticipantsResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status403Forbidden)]
    public async Task<ActionResult<GroupParticipantsResponse>> GetParticipants(Guid id, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var (ok, items, errorCode) = await _groupService.GetParticipantEmailsForOwnerAsync(userId.Value, id, ct);
        if (!ok)
            return StatusCode(403, new ErrorEnvelope(new ApiError(errorCode!)));

        return Ok(new GroupParticipantsResponse(items!));
    }

    [HttpPost("{id:guid}/blocks")]
    [ProducesResponseType(StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> BlockParticipant(Guid id, [FromBody] GroupParticipantEmailRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (req is null)
            return BadRequest(new ErrorEnvelope(new ApiError("participant_email_required")));

        var (ok, errorCode) = await _groupService.BlockParticipantByEmailAsync(userId.Value, id, req.Email ?? "", ct);
        if (!ok)
        {
            if (errorCode == "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            if (errorCode is "user_not_found" or "not_in_group")
                return NotFound(new ErrorEnvelope(new ApiError(errorCode!)));
            return BadRequest(new ErrorEnvelope(new ApiError(errorCode!)));
        }

        return Ok();
    }

    [HttpDelete("{id:guid}/blocks")]
    [ProducesResponseType(StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> UnblockParticipant(Guid id, [FromQuery] string? email, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (string.IsNullOrWhiteSpace(email))
            return BadRequest(new ErrorEnvelope(new ApiError("participant_email_required")));

        var (ok, errorCode) = await _groupService.UnblockParticipantByEmailAsync(userId.Value, id, email, ct);
        if (!ok)
        {
            if (errorCode == "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            if (errorCode is "user_not_found" or "not_blocked")
                return NotFound(new ErrorEnvelope(new ApiError(errorCode!)));
            return BadRequest(new ErrorEnvelope(new ApiError(errorCode!)));
        }

        return Ok();
    }

    [HttpPut("{id:guid}")]
    public async Task<IActionResult> Change(Guid id, [FromBody] GroupChangeRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (req is null)
            return BadRequest(new ErrorEnvelope(new ApiError("group_name_required")));

        var (ok, errorCode) = await _groupService.ChangeNameAsync(userId.Value, id, req.Name ?? "", ct);
        if (!ok)
        {
            if (errorCode is "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            return BadRequest(new ErrorEnvelope(new ApiError(errorCode!)));
        }

        return Ok();
    }

    [HttpDelete("{id:guid}")]
    public async Task<IActionResult> Delete(Guid id, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var (ok, errorCode) = await _groupService.DeleteGroupAsync(userId.Value, id, ct);
        if (!ok)
        {
            if (errorCode == "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            if (errorCode == "group_not_found")
                return NotFound(new ErrorEnvelope(new ApiError("group_not_found")));
            return BadRequest(new ErrorEnvelope(new ApiError(errorCode!)));
        }

        return NoContent();
    }

    [HttpDelete("{id:guid}/members/me")]
    public async Task<IActionResult> Leave(Guid id, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var (ok, errorCode) = await _groupService.LeaveGroupAsync(userId.Value, id, ct);
        if (!ok)
        {
            if (errorCode == "not_in_group")
                return NotFound(new ErrorEnvelope(new ApiError("not_in_group")));
            if (errorCode == "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            if (errorCode == "group_not_found")
                return NotFound(new ErrorEnvelope(new ApiError("group_not_found")));
            return BadRequest(new ErrorEnvelope(new ApiError(errorCode!)));
        }

        return NoContent();
    }

    private Guid? GetCurrentUserId()
    {
        var sub = User.FindFirst(ClaimTypes.NameIdentifier)?.Value
                  ?? User.FindFirst("sub")?.Value;
        return Guid.TryParse(sub, out var id) ? id : null;
    }
}
