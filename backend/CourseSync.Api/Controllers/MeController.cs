using System;
using System.Collections.Generic;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

[ApiController]
[Route("me")]
[Authorize]
[Produces("application/json")]
public sealed class MeController : AuthorizedControllerBase
{
    private static readonly IReadOnlyDictionary<string, ErrorSpec> CurrentGroupErrors =
        new Dictionary<string, ErrorSpec>(StringComparer.Ordinal)
        {
            ["no_group_selected"] = new(400, "no_group_selected"),
            ["forbidden"] = new(403, "forbidden")
        };

    private readonly IGroupService _groupService;

    public MeController(IGroupService groupService) => _groupService = groupService;

    [HttpGet("current-group")]
    public async Task<ActionResult<GroupDetailsResponse>> GetCurrentGroup(CancellationToken ct)
    {
        var (userId, authError) = ResolveCurrentUserIdOrUnauthorized();
        if (authError is not null)
            return authError;

        var (ok, groupId, name, role, groupCode, errorCode) = await _groupService.GetGroupDetailsAsync(userId, ct);
        if (!ok)
            return MapError(errorCode, CurrentGroupErrors, "forbidden", 403);

        return Ok(new GroupDetailsResponse(groupId, name, role, groupCode));
    }

    [HttpPut("current-group")]
    public async Task<IActionResult> SetCurrentGroup([FromBody] SetCurrentGroupRequest req, CancellationToken ct)
    {
        var (userId, authError) = ResolveCurrentUserIdOrUnauthorized();
        if (authError is not null)
            return authError;

        if (req is null)
            return ErrorResponse("group_id_required");

        if (req.GroupId == Guid.Empty)
            return ErrorResponse("group_id_required");

        var (ok, _, _) = await _groupService.ChooseGroupAsync(userId, req.GroupId, ct);
        if (!ok)
            return ErrorResponse("forbidden", 403);

        return Ok();
    }

}
