using System.Security.Claims;
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
public sealed class CalendarController : ControllerBase
{
    private readonly CalendarService _calendarService;
    private readonly UserService _userService;

    public CalendarController(CalendarService calendarService, UserService userService)
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
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (startDate is null != (endDate is null))
        {
            if (startDate is null)
                return BadRequest(new ErrorEnvelope(new ApiError("start_date_required")));
            return BadRequest(new ErrorEnvelope(new ApiError("end_date_required")));
        }

        if (startDate is not null && endDate is not null)
        {
            var rangeValidation = CalendarService.ValidateDateRange(startDate.Value, endDate.Value);
            if (!rangeValidation.Valid)
                return BadRequest(new ErrorEnvelope(new ApiError(rangeValidation.ErrorCode!)));
        }

        var (ok, events, errorCode) = await _calendarService.GetEventsAsync(
            userId.Value,
            startDate,
            endDate,
            ct);

        if (!ok)
        {
            if (errorCode == "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            return BadRequest(new ErrorEnvelope(new ApiError(errorCode!)));
        }

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
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var items = (await _calendarService.GetTypesForUserAsync(userId.Value, ct))
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
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (req is null)
            return BadRequest(new ErrorEnvelope(new ApiError("invalid_request")));

        var nameValidation = CalendarService.ValidateEventName(req.Name);
        if (!nameValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(nameValidation.ErrorCode!)));

        var typeValidation = CalendarService.ValidateEventType(req.EventType);
        if (!typeValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(typeValidation.ErrorCode!)));

        var descriptionValidation = CalendarService.ValidateDescription(req.Description);
        if (!descriptionValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(descriptionValidation.ErrorCode!)));

        var (ok, _, errorCode) = await _calendarService.CreateEventAsync(
            userId.Value,
            req.GroupId,
            req.CourseId,
            req.EventType!,
            req.Name,
            req.Date,
            req.Description,
            ct);

        if (!ok)
        {
            if (errorCode == "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            return BadRequest(new ErrorEnvelope(new ApiError(errorCode!)));
        }

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
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var (ok, evt, errorCode) = await _calendarService.GetEventAsync(
            userId.Value,
            id,
            ct);

        if (!ok)
        {
            if (errorCode == "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            if (errorCode == "calendar_event_not_found")
                return NotFound(new ErrorEnvelope(new ApiError("calendar_event_not_found")));
            return BadRequest(new ErrorEnvelope(new ApiError(errorCode!)));
        }

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
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (req is null)
            return BadRequest(new ErrorEnvelope(new ApiError("invalid_request")));

        var nameValidation = CalendarService.ValidateEventName(req.Name);
        if (!nameValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(nameValidation.ErrorCode!)));

        var typeValidation = CalendarService.ValidateEventType(req.EventType);
        if (!typeValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(typeValidation.ErrorCode!)));

        var descriptionValidation = CalendarService.ValidateDescription(req.Description);
        if (!descriptionValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(descriptionValidation.ErrorCode!)));

        var (ok, errorCode) = await _calendarService.UpdateEventAsync(
            userId.Value,
            id,
            req.CourseId,
            req.EventType!,
            req.Name,
            req.Date,
            req.Description,
            ct);

        if (!ok)
        {
            if (errorCode == "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            if (errorCode == "calendar_event_not_found")
                return NotFound(new ErrorEnvelope(new ApiError("calendar_event_not_found")));
            return BadRequest(new ErrorEnvelope(new ApiError(errorCode!)));
        }

        return Ok();
    }

    [HttpDelete("{id:guid}")]
    [ProducesResponseType(StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> Delete(Guid id, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var (ok, errorCode) = await _calendarService.DeleteEventAsync(
            userId.Value,
            id,
            ct);

        if (!ok)
        {
            if (errorCode == "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            if (errorCode == "calendar_event_not_found")
                return NotFound(new ErrorEnvelope(new ApiError("calendar_event_not_found")));
            return BadRequest(new ErrorEnvelope(new ApiError(errorCode!)));
        }

        return Ok();
    }

    [HttpPatch("{id:guid}")]
    [ProducesResponseType(StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status403Forbidden)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status404NotFound)]
    public async Task<IActionResult> ToggleDone(Guid id, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var (ok, errorCode) = await _calendarService.ToggleDoneAsync(userId.Value, id, ct);
        if (!ok)
        {
            if (errorCode == "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            return NotFound(new ErrorEnvelope(new ApiError("calendar_event_not_found")));
        }

        return Ok();
    }

    private Guid? GetCurrentUserId()
    {
        var sub = User.FindFirst(ClaimTypes.NameIdentifier)?.Value
                  ?? User.FindFirst("sub")?.Value;
        return Guid.TryParse(sub, out var id) ? id : null;
    }
}

