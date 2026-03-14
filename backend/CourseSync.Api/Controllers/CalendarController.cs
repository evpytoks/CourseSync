using System.Security.Claims;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

[ApiController]
[Route("calendar")]
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

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        if (startDate is null || endDate is null)
        {
            if (startDate is null && endDate is null)
                return BadRequest(new ErrorEnvelope(new ApiError("start_and_end_date_required")));
            if (startDate is null)
                return BadRequest(new ErrorEnvelope(new ApiError("start_date_required")));
            return BadRequest(new ErrorEnvelope(new ApiError("end_date_required")));
        }

        var rangeValidation = CalendarService.ValidateDateRange(startDate.Value, endDate.Value);
        if (!rangeValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(rangeValidation.ErrorCode!)));

        var (ok, events, errorCode) = await _calendarService.GetEventsAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            startDate.Value,
            endDate.Value,
            ct);

        if (!ok)
        {
            if (errorCode == "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            return BadRequest(new ErrorEnvelope(new ApiError(errorCode!)));
        }

        var items = events!
            .Select(e => new CalendarListItem(e.Id, e.Name, e.Date))
            .ToList();

        return Ok(new CalendarListResponse(items));
    }

    [HttpPost("add")]
    public async Task<IActionResult> Add([FromBody] AddCalendarEventRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var nameValidation = CalendarService.ValidateEventName(req.Name);
        if (!nameValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(nameValidation.ErrorCode!)));

        var descriptionValidation = CalendarService.ValidateDescription(req.Description);
        if (!descriptionValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(descriptionValidation.ErrorCode!)));

        var (ok, _, errorCode) = await _calendarService.CreateEventAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            req.Name!,
            req.Date,
            req.Description ?? "",
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
    public async Task<ActionResult<CalendarEventDetailsResponse>> Get(Guid id, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var (ok, evt, errorCode) = await _calendarService.GetEventAsync(
            userId.Value,
            user.CurrentGroupId.Value,
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

        var resp = new CalendarEventDetailsResponse(evt!.Name, evt.Date, evt.Description);
        return Ok(resp);
    }

    [HttpPut("{id:guid}/change")]
    public async Task<IActionResult> Change(Guid id, [FromBody] UpdateCalendarEventRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var nameValidation = CalendarService.ValidateEventName(req.Name);
        if (!nameValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(nameValidation.ErrorCode!)));

        var descriptionValidation = CalendarService.ValidateDescription(req.Description);
        if (!descriptionValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(descriptionValidation.ErrorCode!)));

        var (ok, errorCode) = await _calendarService.UpdateEventAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            req.Name!,
            req.Date,
            req.Description ?? "",
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

    [HttpDelete("{id:guid}/delete")]
    public async Task<IActionResult> Delete(Guid id, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var (ok, errorCode) = await _calendarService.DeleteEventAsync(
            userId.Value,
            user.CurrentGroupId.Value,
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

    private Guid? GetCurrentUserId()
    {
        var sub = User.FindFirst(ClaimTypes.NameIdentifier)?.Value
                  ?? User.FindFirst("sub")?.Value;
        return Guid.TryParse(sub, out var id) ? id : null;
    }
}

