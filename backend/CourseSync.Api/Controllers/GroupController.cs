using System;
using System.Collections.Generic;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

[ApiController]
[Route("groups")]
[Authorize]
[Produces("application/json")]
public sealed class GroupController : AuthorizedControllerBase
{
    private static readonly IReadOnlyDictionary<string, ErrorSpec> JoinErrors =
        new Dictionary<string, ErrorSpec>(StringComparer.Ordinal)
        {
            ["group_join_blocked"] = new(403, "group_join_blocked"),
            ["invalid_code_format"] = new(400, "invalid_code_format")
        };

    private static readonly IReadOnlyDictionary<string, ErrorSpec> ParticipantMutateErrors =
        new Dictionary<string, ErrorSpec>(StringComparer.Ordinal)
        {
            ["forbidden"] = new(403, "forbidden"),
            ["user_not_found"] = new(404, "user_not_found"),
            ["not_in_group"] = new(404, "not_in_group"),
            ["not_blocked"] = new(404, "not_blocked")
        };

    private static readonly IReadOnlyDictionary<string, ErrorSpec> GroupMutateErrors =
        new Dictionary<string, ErrorSpec>(StringComparer.Ordinal)
        {
            ["forbidden"] = new(403, "forbidden"),
            ["group_not_found"] = new(404, "group_not_found"),
            ["not_in_group"] = new(404, "not_in_group")
        };

    private readonly IGroupService _groupService;

    public GroupController(IGroupService groupService) => _groupService = groupService;

    [HttpGet]
    public async Task<ActionResult<GroupListResponse>> List(CancellationToken ct)
    {
        var (userId, authError) = ResolveCurrentUserIdOrUnauthorized();
        if (authError is not null)
            return authError;

        var dtos = await _groupService.GetUserGroupsAsync(userId, ct);
        var items = dtos.Select(d => new GroupListItem(d.Id, d.Name, d.Role, d.GroupCode, d.CreatorEmail)).ToList();
        return Ok(new GroupListResponse(items));
    }

    [HttpGet("owned")]
    public async Task<ActionResult<OwnerGroupListResponse>> OwnerList(CancellationToken ct)
    {
        var (userId, authError) = ResolveCurrentUserIdOrUnauthorized();
        if (authError is not null)
            return authError;

        var dtos = await _groupService.GetOwnerGroupsAsync(userId, ct);
        var items = dtos.Select(d => new OwnerGroupListItem(d.Id, d.Name)).ToList();
        return Ok(new OwnerGroupListResponse(items));
    }

    [HttpPost]
    public async Task<IActionResult> Create([FromBody] CreateGroupRequest req, CancellationToken ct)
    {
        var (userId, authError) = ResolveCurrentUserIdOrUnauthorized();
        if (authError is not null)
            return authError;

        if (req is null)
            return ErrorResponse("group_name_required");

        var validation = GroupService.ValidateGroupName(req.Name);
        if (!validation.Valid)
            return ErrorResponse(validation.ErrorCode!);

        var result = await _groupService.CreateGroupAsync(userId, req.Name!.Trim(), ct);
        if (result is null)
            return ErrorResponse("group_creation_failed", 500);

        return Ok();
    }

    [HttpPost("join")]
    public async Task<IActionResult> Join([FromBody] GroupJoinRequest req, CancellationToken ct)
    {
        var (userId, authError) = ResolveCurrentUserIdOrUnauthorized();
        if (authError is not null)
            return authError;

        if (req is null)
            return ErrorResponse("invalid_code_format");

        var codeValidation = GroupService.ValidateGroupCode(req.Code);
        if (!codeValidation.Valid)
            return ErrorResponse(codeValidation.ErrorCode!);

        var result = await _groupService.JoinByCodeAsync(userId, (req.Code ?? "").Trim(), ct);
        if (!result.Ok)
            return MapError(result.ErrorCode, JoinErrors, "group_not_found", 404);

        return Ok();
    }

    [HttpGet("{id:guid}/participants")]
    [ProducesResponseType(typeof(GroupParticipantsResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status403Forbidden)]
    public async Task<ActionResult<GroupParticipantsResponse>> GetParticipants(Guid id, CancellationToken ct)
    {
        var (userId, authError) = ResolveCurrentUserIdOrUnauthorized();
        if (authError is not null)
            return authError;

        var (ok, items, errorCode) = await _groupService.GetParticipantEmailsForOwnerAsync(userId, id, ct);
        if (!ok)
            return ErrorResponse(errorCode!, 403);

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
        var (userId, authError) = ResolveCurrentUserIdOrUnauthorized();
        if (authError is not null)
            return authError;

        if (req is null)
            return ErrorResponse("participant_email_required");

        var (ok, errorCode) = await _groupService.BlockParticipantByEmailAsync(userId, id, req.Email ?? "", ct);
        if (!ok)
            return MapError(errorCode, ParticipantMutateErrors);

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
        var (userId, authError) = ResolveCurrentUserIdOrUnauthorized();
        if (authError is not null)
            return authError;

        if (string.IsNullOrWhiteSpace(email))
            return ErrorResponse("participant_email_required");

        var (ok, errorCode) = await _groupService.UnblockParticipantByEmailAsync(userId, id, email, ct);
        if (!ok)
            return MapError(errorCode, ParticipantMutateErrors);

        return Ok();
    }

    [HttpPut("{id:guid}")]
    public async Task<IActionResult> Change(Guid id, [FromBody] GroupChangeRequest req, CancellationToken ct)
    {
        var (userId, authError) = ResolveCurrentUserIdOrUnauthorized();
        if (authError is not null)
            return authError;

        if (req is null)
            return ErrorResponse("group_name_required");

        var (ok, errorCode) = await _groupService.ChangeNameAsync(userId, id, req.Name ?? "", ct);
        if (!ok)
            return MapError(errorCode, GroupMutateErrors);

        return Ok();
    }

    [HttpDelete("{id:guid}")]
    public async Task<IActionResult> Delete(Guid id, CancellationToken ct)
    {
        var (userId, authError) = ResolveCurrentUserIdOrUnauthorized();
        if (authError is not null)
            return authError;

        var (ok, errorCode) = await _groupService.DeleteGroupAsync(userId, id, ct);
        if (!ok)
            return MapError(errorCode, GroupMutateErrors);

        return NoContent();
    }

    [HttpDelete("{id:guid}/members/me")]
    public async Task<IActionResult> Leave(Guid id, CancellationToken ct)
    {
        var (userId, authError) = ResolveCurrentUserIdOrUnauthorized();
        if (authError is not null)
            return authError;

        var (ok, errorCode) = await _groupService.LeaveGroupAsync(userId, id, ct);
        if (!ok)
            return MapError(errorCode, GroupMutateErrors);

        return NoContent();
    }

}
