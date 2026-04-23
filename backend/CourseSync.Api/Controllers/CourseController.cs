using CourseSync.Api.Application.Courses;
using CourseSync.Api.Infrastructure;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

[ApiController]
[Route("courses")]
[Authorize]
[Produces("application/json")]
public sealed partial class CourseController : AuthorizedControllerBase
{
    private readonly ICourseQueryService _courseQuery;
    private readonly ICourseCommandService _courseCommand;
    private readonly ICourseGradingService _courseGrading;
    private readonly ICourseCumulativeGradeService _courseCumulativeGrade;
    private readonly ICourseMaterialService _courseMaterialService;
    private readonly IUserService _userService;
    private readonly ICalendarService _calendarService;

    public CourseController(
        ICourseQueryService courseQuery,
        ICourseCommandService courseCommand,
        ICourseGradingService courseGrading,
        ICourseCumulativeGradeService courseCumulativeGrade,
        ICourseMaterialService courseMaterialService,
        IUserService userService,
        ICalendarService calendarService)
    {
        _courseQuery = courseQuery;
        _courseCommand = courseCommand;
        _courseGrading = courseGrading;
        _courseCumulativeGrade = courseCumulativeGrade;
        _courseMaterialService = courseMaterialService;
        _userService = userService;
        _calendarService = calendarService;
    }

    [HttpGet]
    public async Task<ActionResult<CourseListResponse>> List(CancellationToken ct)
    {
        var (authErr, _, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var dtos = await _courseQuery.GetByGroupIdAsync(groupId, ct);
        var items = dtos.Select(d => new CourseListItem(d.Id, d.Name)).ToList();
        return Ok(new CourseListResponse(items));
    }

    [HttpGet("~/groups/{groupId:guid}/courses")]
    public async Task<ActionResult<CourseListResponse>> ListByGroup(Guid groupId, CancellationToken ct)
    {
        var (authErr, userId, _) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;

        var (ok, dtos, errorCode) = await _courseQuery.GetByGroupIdForOwnerAsync(userId, groupId, ct);
        if (!ok)
            return ForbiddenOnlyError(errorCode!);

        var items = dtos!.Select(d => new CourseListItem(d.Id, d.Name)).ToList();
        return Ok(new CourseListResponse(items));
    }

    [HttpPost]
    public async Task<IActionResult> Add([FromBody] AddCourseRequest req, CancellationToken ct)
    {
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;

        if (req is null)
            return ErrorResponse("course_name_required");

        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var nameValidation = CourseInputRules.ValidateCourseName(req.Name);
        if (!nameValidation.Valid)
            return ErrorResponse(nameValidation.ErrorCode!);

        var generalInfoValidation = CourseInputRules.ValidateGeneralInfo(req.GeneralInfo);
        if (!generalInfoValidation.Valid)
            return ErrorResponse(generalInfoValidation.ErrorCode!);

        var contactsValidation = CourseInputRules.ValidateContacts(req.Contacts);
        if (!contactsValidation.Valid)
            return ErrorResponse(contactsValidation.ErrorCode!);

        var usefulLinksValidation = CourseInputRules.ValidateUsefulLinks(req.UsefulLinks);
        if (!usefulLinksValidation.Valid)
            return ErrorResponse(usefulLinksValidation.ErrorCode!);

        var (ok, _, _, _, _, _, errorCode) = await _courseCommand.CreateCourseAsync(
            userId,
            groupId,
            req.Name!.Trim(),
            req.GeneralInfo ?? "",
            req.Contacts ?? Array.Empty<CourseContactPersonItem>(),
            req.UsefulLinks ?? Array.Empty<CourseUsefulLinkItem>(),
            ct);

        if (!ok)
            return ForbiddenOnlyError(errorCode!);

        return Ok();
    }

    [HttpGet("{id:guid}")]
    public async Task<ActionResult<CourseDetailsResponse>> GetById(Guid id, CancellationToken ct)
    {
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var (ok, data, errorCode) = await _courseQuery.GetCourseByIdAsync(userId, groupId, id, ct);

        if (!ok)
            return MapError(errorCode, ForbiddenOnlyErrors, "course_not_in_group");

        return Ok(new CourseDetailsResponse(data!.Id, data.Name, data.GeneralInfo, data.Contacts, data.UsefulLinks));
    }

    [HttpGet("{id:guid}/calendar")]
    [ProducesResponseType(typeof(CalendarListResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status403Forbidden)]
    public async Task<ActionResult<CalendarListResponse>> GetCalendar(Guid id, CancellationToken ct)
    {
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var (ok, events, errorCode) = await _calendarService.GetEventsForCourseAsync(userId, groupId, id, ct);

        if (!ok)
            return MapError(errorCode, ForbiddenOnlyErrors, "course_not_in_group");

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

    [HttpPut("{id:guid}")]
    public async Task<IActionResult> Change(Guid id, [FromBody] ChangeCourseRequest req, CancellationToken ct)
    {
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;

        if (req is null)
            return ErrorResponse("course_name_required");

        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var nameValidation = CourseInputRules.ValidateCourseName(req.Name);
        if (!nameValidation.Valid)
            return ErrorResponse(nameValidation.ErrorCode!);

        var generalInfoValidation = CourseInputRules.ValidateGeneralInfo(req.GeneralInfo);
        if (!generalInfoValidation.Valid)
            return ErrorResponse(generalInfoValidation.ErrorCode!);

        var contactsValidation = CourseInputRules.ValidateContacts(req.Contacts);
        if (!contactsValidation.Valid)
            return ErrorResponse(contactsValidation.ErrorCode!);

        var usefulLinksValidation = CourseInputRules.ValidateUsefulLinks(req.UsefulLinks);
        if (!usefulLinksValidation.Valid)
            return ErrorResponse(usefulLinksValidation.ErrorCode!);

        var (ok, errorCode) = await _courseCommand.UpdateCourseAsync(
            userId,
            groupId,
            id,
            req.Name!.Trim(),
            req.GeneralInfo ?? "",
            req.Contacts ?? Array.Empty<CourseContactPersonItem>(),
            req.UsefulLinks ?? Array.Empty<CourseUsefulLinkItem>(),
            ct);

        if (!ok)
            return MapError(errorCode, ForbiddenOnlyErrors, "course_not_in_group");

        return NoContent();
    }

    [HttpDelete("{id:guid}")]
    public async Task<IActionResult> Delete(Guid id, CancellationToken ct)
    {
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var (ok, errorCode) = await _courseCommand.DeleteCourseAsync(userId, groupId, id, ct);

        if (!ok)
            return MapError(errorCode, ForbiddenOnlyErrors, "course_not_in_group");

        return NoContent();
    }

}
