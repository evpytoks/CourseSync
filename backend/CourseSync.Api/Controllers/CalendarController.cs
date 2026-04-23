using System;
using System.Collections.Generic;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

[ApiController]
[Route("calendar-events")]
[Authorize]
[Produces("application/json")]
public sealed class CalendarController : AuthorizedControllerBase
{
    private static readonly IReadOnlyDictionary<string, ErrorSpec> ForbiddenOnlyErrors =
        new Dictionary<string, ErrorSpec>(StringComparer.Ordinal)
        {
            ["forbidden"] = new(403, "forbidden")
        };

    private static readonly IReadOnlyDictionary<string, ErrorSpec> CalendarEntityErrors =
        new Dictionary<string, ErrorSpec>(StringComparer.Ordinal)
        {
            ["forbidden"] = new(403, "forbidden"),
            ["calendar_event_not_found"] = new(404, "calendar_event_not_found")
        };

    private readonly ICalendarService _calendarService;
    private readonly IUserService _userService;

    public CalendarController(ICalendarService calendarService, IUserService userService)
    {
        _calendarService = calendarService;
        _userService = userService;
    }

    [HttpGet]
    [ProducesResponseType(typeof(CalendarListResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    public async Task<ActionResult<CalendarListResponse>> List(
        [FromQuery] DateOnly? startDate,
        [FromQuery] DateOnly? endDate,
        CancellationToken ct)
    {
        var (userId, authError) = await TryResolveAuthenticatedUserAsync(ct);
        if (authError is not null)
            return authError;

        if (startDate is null != (endDate is null))
        {
            if (startDate is null)
                return ErrorResponse("start_date_required");
            return ErrorResponse("end_date_required");
        }

        if (startDate is not null && endDate is not null)
        {
            var rangeValidation = CalendarService.ValidateDateRange(startDate.Value, endDate.Value);
            if (!rangeValidation.Valid)
                return ErrorResponse(rangeValidation.ErrorCode!);
        }

        var (ok, events, errorCode) = await _calendarService.GetEventsAsync(
            userId,
            startDate,
            endDate,
            ct);

        if (!ok)
            return MapError(errorCode, ForbiddenOnlyErrors);

        var items = events!
            .Select(e => new CalendarListItem(
                e.Id,
                e.GroupId,
                e.GroupName,
                e.CourseId,
                e.CourseName,
                e.EventType,
                e.EventColor,
                e.Name,
                e.Date,
                e.IsDone))
            .ToList();

        return Ok(new CalendarListResponse(items));
    }

    [HttpGet("types")]
    [ProducesResponseType(typeof(CalendarEventTypeColorsResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    public async Task<ActionResult<CalendarEventTypeColorsResponse>> EventTypes(CancellationToken ct)
    {
        var (userId, authError) = await TryResolveAuthenticatedUserAsync(ct);
        if (authError is not null)
            return authError;

        var items = (await _calendarService.GetTypesForUserAsync(userId, ct))
            .Select(x => new CalendarEventTypeColorItem(x.Type, x.Color))
            .ToList();

        return Ok(new CalendarEventTypeColorsResponse(items));
    }

    [HttpPost]
    [ProducesResponseType(StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status403Forbidden)]
    public async Task<IActionResult> Add([FromBody] AddCalendarEventRequest req, CancellationToken ct)
    {
        var (userId, authError) = await TryResolveAuthenticatedUserAsync(ct);
        if (authError is not null)
            return authError;

        if (req is null)
            return ErrorResponse("invalid_request");

        var nameValidation = CalendarService.ValidateEventName(req.Name);
        if (!nameValidation.Valid)
            return ErrorResponse(nameValidation.ErrorCode!);

        var typeValidation = CalendarService.ValidateEventType(req.EventType);
        if (!typeValidation.Valid)
            return ErrorResponse(typeValidation.ErrorCode!);

        var descriptionValidation = CalendarService.ValidateDescription(req.Description);
        if (!descriptionValidation.Valid)
            return ErrorResponse(descriptionValidation.ErrorCode!);

        var (ok, _, errorCode) = await _calendarService.CreateEventAsync(
            userId,
            req.GroupId,
            req.CourseId,
            req.EventType!,
            req.Name,
            req.Date,
            req.Description,
            ct);

        if (!ok)
            return MapError(errorCode, ForbiddenOnlyErrors);

        return Ok();
    }

    [HttpGet("{id:guid}")]
    [ProducesResponseType(typeof(CalendarEventDetailsResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status404NotFound)]
    public async Task<ActionResult<CalendarEventDetailsResponse>> Get(Guid id, CancellationToken ct)
    {
        var (userId, authError) = await TryResolveAuthenticatedUserAsync(ct);
        if (authError is not null)
            return authError;

        var (ok, evt, errorCode) = await _calendarService.GetEventAsync(
            userId,
            id,
            ct);

        if (!ok)
            return MapError(errorCode, CalendarEntityErrors);

        var resp = new CalendarEventDetailsResponse(
            evt!.GroupId,
            evt.GroupName,
            evt.CourseId,
            evt.CourseName,
            evt.EventType,
            evt.EventColor,
            evt.Name,
            evt.Date,
            evt.Description,
            evt.IsDone);
        return Ok(resp);
    }

    [HttpPut("{id:guid}")]
    [ProducesResponseType(StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> Change(Guid id, [FromBody] UpdateCalendarEventRequest req, CancellationToken ct)
    {
        var (userId, authError) = await TryResolveAuthenticatedUserAsync(ct);
        if (authError is not null)
            return authError;

        if (req is null)
            return ErrorResponse("invalid_request");

        var nameValidation = CalendarService.ValidateEventName(req.Name);
        if (!nameValidation.Valid)
            return ErrorResponse(nameValidation.ErrorCode!);

        var typeValidation = CalendarService.ValidateEventType(req.EventType);
        if (!typeValidation.Valid)
            return ErrorResponse(typeValidation.ErrorCode!);

        var descriptionValidation = CalendarService.ValidateDescription(req.Description);
        if (!descriptionValidation.Valid)
            return ErrorResponse(descriptionValidation.ErrorCode!);

        var (ok, errorCode) = await _calendarService.UpdateEventAsync(
            userId,
            id,
            req.CourseId,
            req.EventType!,
            req.Name,
            req.Date,
            req.Description,
            ct);

        if (!ok)
            return MapError(errorCode, CalendarEntityErrors);

        return Ok();
    }

    [HttpDelete("{id:guid}")]
    [ProducesResponseType(StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> Delete(Guid id, CancellationToken ct)
    {
        var (userId, authError) = await TryResolveAuthenticatedUserAsync(ct);
        if (authError is not null)
            return authError;

        var (ok, errorCode) = await _calendarService.DeleteEventAsync(
            userId,
            id,
            ct);

        if (!ok)
            return MapError(errorCode, CalendarEntityErrors);

        return Ok();
    }

    [HttpPatch("{id:guid}")]
    [ProducesResponseType(StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> ToggleDone(Guid id, CancellationToken ct)
    {
        var (userId, authError) = await TryResolveAuthenticatedUserAsync(ct);
        if (authError is not null)
            return authError;

        var (ok, errorCode) = await _calendarService.ToggleDoneAsync(userId, id, ct);
        if (!ok)
            return MapError(errorCode, CalendarEntityErrors, "calendar_event_not_found", 404);

        return Ok();
    }

    private async Task<(Guid UserId, ActionResult? Error)> TryResolveAuthenticatedUserAsync(CancellationToken ct)
    {
        var (userId, _, error) = await ResolveUserOrUnauthorizedAsync(_userService, ct);
        return (userId, error);
    }
}

