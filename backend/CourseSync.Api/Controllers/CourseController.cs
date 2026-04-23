using System.Security.Claims;
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
public sealed class CourseController : ControllerBase
{
    private readonly ICourseQueryService _courseQuery;
    private readonly ICourseCommandService _courseCommand;
    private readonly ICourseGradingService _courseGrading;
    private readonly ICourseCumulativeGradeService _courseCumulativeGrade;
    private readonly CourseMaterialService _courseMaterialService;
    private readonly UserService _userService;
    private readonly CalendarService _calendarService;

    public CourseController(
        ICourseQueryService courseQuery,
        ICourseCommandService courseCommand,
        ICourseGradingService courseGrading,
        ICourseCumulativeGradeService courseCumulativeGrade,
        CourseMaterialService courseMaterialService,
        UserService userService,
        CalendarService calendarService)
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
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var dtos = await _courseQuery.GetByGroupIdAsync(user.CurrentGroupId.Value, ct);
        var items = dtos.Select(d => new CourseListItem(d.Id, d.Name)).ToList();
        return Ok(new CourseListResponse(items));
    }

    [HttpGet("~/groups/{groupId:guid}/courses")]
    public async Task<ActionResult<CourseListResponse>> ListByGroup(Guid groupId, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var (ok, dtos, errorCode) = await _courseQuery.GetByGroupIdForOwnerAsync(userId.Value, groupId, ct);
        if (!ok)
        {
            if (errorCode == "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            return BadRequest(new ErrorEnvelope(new ApiError(errorCode!)));
        }

        var items = dtos!.Select(d => new CourseListItem(d.Id, d.Name)).ToList();
        return Ok(new CourseListResponse(items));
    }

    [HttpPost]
    public async Task<IActionResult> Add([FromBody] AddCourseRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (req is null)
            return BadRequest(new ErrorEnvelope(new ApiError("course_name_required")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var nameValidation = CourseInputRules.ValidateCourseName(req.Name);
        if (!nameValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(nameValidation.ErrorCode!)));

        var generalInfoValidation = CourseInputRules.ValidateGeneralInfo(req.GeneralInfo);
        if (!generalInfoValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(generalInfoValidation.ErrorCode!)));

        var contactsValidation = CourseInputRules.ValidateContacts(req.Contacts);
        if (!contactsValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(contactsValidation.ErrorCode!)));

        var usefulLinksValidation = CourseInputRules.ValidateUsefulLinks(req.UsefulLinks);
        if (!usefulLinksValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(usefulLinksValidation.ErrorCode!)));

        var (ok, courseId, name, generalInfo, contacts, usefulLinks, errorCode) = await _courseCommand.CreateCourseAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            req.Name!.Trim(),
            req.GeneralInfo ?? "",
            req.Contacts ?? Array.Empty<CourseContactPersonItem>(),
            req.UsefulLinks ?? Array.Empty<CourseUsefulLinkItem>(),
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
    public async Task<ActionResult<CourseDetailsResponse>> GetById(Guid id, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var (ok, data, errorCode) = await _courseQuery.GetCourseByIdAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            ct);

        if (!ok)
        {
            if (errorCode == "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            return BadRequest(new ErrorEnvelope(new ApiError("course_not_in_group")));
        }

        return Ok(new CourseDetailsResponse(data!.Id, data.Name, data.GeneralInfo, data.Contacts, data.UsefulLinks));
    }

    [HttpGet("{id:guid}/calendar")]
    [ProducesResponseType(typeof(CalendarListResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status400BadRequest)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(typeof(ErrorEnvelope), StatusCodes.Status403Forbidden)]
    public async Task<ActionResult<CalendarListResponse>> GetCalendar(Guid id, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var (ok, events, errorCode) = await _calendarService.GetEventsForCourseAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            ct);

        if (!ok)
        {
            if (errorCode == "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            return BadRequest(new ErrorEnvelope(new ApiError("course_not_in_group")));
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

    [HttpPost("{id:guid}/grading")]
    public async Task<IActionResult> SaveGrading(Guid id, [FromBody] SaveCourseGradingRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (req is null)
            return BadRequest(new ErrorEnvelope(new ApiError("invalid_request")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var elements = (req.Elements ?? Array.Empty<CourseGradingElementRequest>())
            .Select(e => (e.Name ?? "", e.Coefficient, e.Block ?? 0m))
            .ToList();

        var (ok, errorCode) = await _courseGrading.SaveGradingAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            req.Text ?? "",
            elements,
            ct);

        if (!ok)
            return GradingError(errorCode!);

        return NoContent();
    }

    [HttpGet("{id:guid}/grading/text")]
    public async Task<IActionResult> GetGradingText(Guid id, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var (ok, text, errorCode) = await _courseGrading.GetGradingTextAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            ct);

        if (!ok)
            return GradingError(errorCode!);

        return Ok(new CourseGradingTextResponse(text ?? ""));
    }

    [HttpGet("{id:guid}/grading/elements")]
    public async Task<IActionResult> GradingElements(Guid id, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var (ok, items, errorCode) = await _courseGrading.GetGradingElementOptionsAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            ct);

        if (!ok)
            return GradingError(errorCode!);

        var list = items!.Select(x => new CourseGradingElementOptionItem(x.Id, x.Name)).ToList();
        return Ok(new CourseGradingElementListResponse(list));
    }

    [HttpGet("{id:guid}/grading")]
    public async Task<IActionResult> GetGrading(Guid id, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var (ok, elements, errorCode) = await _courseGrading.GetGradingAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            ct);

        if (!ok)
            return GradingError(errorCode!);

        var payload = (elements ?? Array.Empty<GradingElementDto>())
            .Select(e => new CourseGradingElementResponse(
                e.Name,
                e.Coefficient,
                e.Block,
                e.Count,
                e.AverageScore,
                e.IsBlocked))
            .ToList();
        var averageGrade = payload.Count == 0
            ? 0m
            : decimal.Round(
                payload.Sum(x => x.Coefficient * x.AverageScore),
                2,
                MidpointRounding.AwayFromZero);
        return Ok(new CourseGradingResponse(payload, averageGrade));
    }

    [HttpGet("{id:guid}/grading/scores")]
    public async Task<IActionResult> GetGradingScores(Guid id, [FromQuery(Name = "name")] string? name, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var trimmed = (name ?? "").Trim();
        if (trimmed.Length == 0)
        {
            var (allOk, allElements, allError) = await _courseGrading.GetAllGradingScoresAsync(
                userId.Value,
                user.CurrentGroupId.Value,
                id,
                ct);
            if (!allOk)
                return GradingError(allError!);
            var blocks = (allElements ?? Array.Empty<GradingElementScoresRow>())
                .Select(e => new CourseGradingScoresResponse(e.Name, e.Count, e.Scores))
                .ToList();
            return Ok(new CourseGradingAllScoresResponse(blocks));
        }

        var (ok, elementName, count, scores, errorCode) = await _courseGrading.GetGradingScoresAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            trimmed,
            ct);

        if (!ok)
            return GradingError(errorCode!);

        var payload = new CourseGradingScoresResponse(
            elementName,
            count,
            scores ?? Array.Empty<decimal>());
        return Ok(payload);
    }

    [HttpPut("{id:guid}/grading/scores")]
    public async Task<IActionResult> UpdateGradingScores(Guid id, [FromBody] UpdateCourseGradingScoresRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (req is null)
            return BadRequest(new ErrorEnvelope(new ApiError("grading_element_name_required")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var scores = req.Scores ?? Array.Empty<decimal>();
        var (ok, errorCode) = await _courseGrading.UpdateGradingScoresAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            (req.Name ?? "").Trim(),
            scores,
            ct);

        if (!ok)
            return GradingError(errorCode!);

        return NoContent();
    }

    [HttpPut("{id:guid}/cumulative-grades")]
    public async Task<IActionResult> SaveCumulativeGrade(Guid id, [FromBody] SaveCourseCumulativeGradeRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (req is null)
            return BadRequest(new ErrorEnvelope(new ApiError("cumulative_grade_elements_required")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var (ok, errorCode) = await _courseCumulativeGrade.SaveCumulativeGradeAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            req.ElementIds,
            req.Block,
            req.Automatic,
            ct);

        if (!ok)
            return GradingError(errorCode!);

        return NoContent();
    }

    [HttpGet("{id:guid}/cumulative-grades")]
    public async Task<IActionResult> GetCumulativeGrade(Guid id, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var (ok, data, errorCode) = await _courseCumulativeGrade.GetCumulativeGradeAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            ct);

        if (!ok)
            return GradingError(errorCode!);

        return Ok(new CourseCumulativeGradeResponse(
            data!.Value,
            data.Block,
            data.Automatic,
            data.IsBlocked,
            data.IsAuto,
            data.ElementNames));
    }

    [HttpPut("{id:guid}")]
    public async Task<IActionResult> Change(Guid id, [FromBody] ChangeCourseRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (req is null)
            return BadRequest(new ErrorEnvelope(new ApiError("course_name_required")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var nameValidation = CourseInputRules.ValidateCourseName(req.Name);
        if (!nameValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(nameValidation.ErrorCode!)));

        var generalInfoValidation = CourseInputRules.ValidateGeneralInfo(req.GeneralInfo);
        if (!generalInfoValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(generalInfoValidation.ErrorCode!)));

        var contactsValidation = CourseInputRules.ValidateContacts(req.Contacts);
        if (!contactsValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(contactsValidation.ErrorCode!)));

        var usefulLinksValidation = CourseInputRules.ValidateUsefulLinks(req.UsefulLinks);
        if (!usefulLinksValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(usefulLinksValidation.ErrorCode!)));

        var (ok, errorCode) = await _courseCommand.UpdateCourseAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            req.Name!.Trim(),
            req.GeneralInfo ?? "",
            req.Contacts ?? Array.Empty<CourseContactPersonItem>(),
            req.UsefulLinks ?? Array.Empty<CourseUsefulLinkItem>(),
            ct);

        if (!ok)
        {
            if (errorCode == "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            return BadRequest(new ErrorEnvelope(new ApiError("course_not_in_group")));
        }

        return NoContent();
    }

    [HttpDelete("{id:guid}")]
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

        var (ok, errorCode) = await _courseCommand.DeleteCourseAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            ct);

        if (!ok)
        {
            if (errorCode == "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            return BadRequest(new ErrorEnvelope(new ApiError("course_not_in_group")));
        }

        return NoContent();
    }

    [HttpGet("{id:guid}/general-materials")]
    public async Task<IActionResult> ListGeneralMaterials(Guid id, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var (ok, items, errorCode) = await _courseMaterialService.ListGeneralAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            ct);

        if (!ok)
            return MaterialAccessError(errorCode!);

        var list = items!.Select(m => new CourseMaterialListItem(
            m.Id,
            m.Name,
            m.AuthorEmail,
            m.CreatedAt)).ToList();
        return Ok(new CourseMaterialListResponse(list));
    }

    [HttpPost("{id:guid}/general-materials")]
    [Consumes("multipart/form-data")]
    [RequestSizeLimit(CourseMaterialUploadLimits.MaxMultipartRequestBytes)]
    public async Task<IActionResult> AddGeneralMaterial(Guid id, IFormFile? file, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        if (file is null)
            return BadRequest(new ErrorEnvelope(new ApiError("file_required")));

        var (ok, errorCode) = await _courseMaterialService.AddGeneralAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            user.Email,
            file,
            ct);

        if (!ok)
            return AddMaterialError(errorCode!);

        return NoContent();
    }

    [HttpGet("{id:guid}/personal-materials")]
    public async Task<IActionResult> ListPersonalMaterials(Guid id, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var (ok, items, errorCode) = await _courseMaterialService.ListPersonalAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            ct);

        if (!ok)
            return MaterialAccessError(errorCode!);

        var list = items!.Select(m => new CoursePersonalMaterialListItem(
            m.Id,
            m.Name,
            m.AuthorEmail,
            m.CreatedAt,
            m.IsCreator)).ToList();
        return Ok(new CoursePersonalMaterialListResponse(list));
    }

    [HttpPost("{id:guid}/personal-materials")]
    [Consumes("multipart/form-data")]
    [RequestSizeLimit(CourseMaterialUploadLimits.MaxMultipartRequestBytes)]
    public async Task<IActionResult> AddPersonalMaterial(Guid id, IFormFile? file, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        if (file is null)
            return BadRequest(new ErrorEnvelope(new ApiError("file_required")));

        var (ok, errorCode) = await _courseMaterialService.AddPersonalAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            user.Email,
            file,
            ct);

        if (!ok)
            return AddMaterialError(errorCode!);

        return NoContent();
    }

    [HttpDelete("{id:guid}/general-materials/{materialId:guid}")]
    public async Task<IActionResult> DeleteGeneralMaterial(Guid id, Guid materialId, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var (ok, errorCode) = await _courseMaterialService.DeleteGeneralAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            materialId,
            ct);

        if (!ok)
            return DeleteMaterialError(errorCode!);

        return NoContent();
    }

    [HttpGet("{id:guid}/general-materials/{materialId:guid}/pdf")]
    public async Task<IActionResult> OpenGeneralMaterialPdf(Guid id, Guid materialId, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var (ok, file, errorCode) = await _courseMaterialService.OpenGeneralPdfAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            materialId,
            ct);

        if (!ok)
            return DownloadMaterialError(errorCode!);

        return File(file!.Content, "application/pdf", file.FileName, enableRangeProcessing: true);
    }

    [HttpDelete("{id:guid}/personal-materials/{materialId:guid}")]
    public async Task<IActionResult> DeletePersonalMaterial(Guid id, Guid materialId, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var (ok, errorCode) = await _courseMaterialService.DeletePersonalAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            materialId,
            ct);

        if (!ok)
            return DeleteMaterialError(errorCode!);

        return NoContent();
    }

    [HttpGet("{id:guid}/personal-materials/{materialId:guid}/pdf")]
    public async Task<IActionResult> OpenPersonalMaterialPdf(Guid id, Guid materialId, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (user.CurrentGroupId is null)
            return BadRequest(new ErrorEnvelope(new ApiError("no_group_selected")));

        var (ok, file, errorCode) = await _courseMaterialService.OpenPersonalPdfAsync(
            userId.Value,
            user.CurrentGroupId.Value,
            id,
            materialId,
            ct);

        if (!ok)
            return DownloadMaterialError(errorCode!);

        return File(file!.Content, "application/pdf", file.FileName, enableRangeProcessing: true);
    }

    private IActionResult MaterialAccessError(string errorCode)
    {
        if (errorCode == "forbidden")
            return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
        return BadRequest(new ErrorEnvelope(new ApiError("course_not_in_group")));
    }

    private IActionResult AddMaterialError(string errorCode)
    {
        if (errorCode == "forbidden")
            return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
        if (errorCode == "course_not_in_group")
            return BadRequest(new ErrorEnvelope(new ApiError("course_not_in_group")));
        return BadRequest(new ErrorEnvelope(new ApiError(errorCode)));
    }

    private IActionResult DeleteMaterialError(string errorCode)
    {
        if (errorCode == "forbidden")
            return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
        if (errorCode == "course_not_in_group")
            return BadRequest(new ErrorEnvelope(new ApiError("course_not_in_group")));
        if (errorCode == "material_not_found")
            return NotFound(new ErrorEnvelope(new ApiError("material_not_found")));
        if (errorCode == "storage_delete_failed")
            return StatusCode(503, new ErrorEnvelope(new ApiError("storage_delete_failed")));
        return BadRequest(new ErrorEnvelope(new ApiError(errorCode)));
    }

    private IActionResult DownloadMaterialError(string errorCode)
    {
        if (errorCode == "forbidden")
            return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
        if (errorCode == "course_not_in_group")
            return BadRequest(new ErrorEnvelope(new ApiError("course_not_in_group")));
        if (errorCode == "material_not_found")
            return NotFound(new ErrorEnvelope(new ApiError("material_not_found")));
        return BadRequest(new ErrorEnvelope(new ApiError(errorCode)));
    }

    private IActionResult GradingError(string errorCode)
    {
        if (errorCode == "forbidden")
            return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
        if (errorCode == "course_not_in_group")
            return BadRequest(new ErrorEnvelope(new ApiError("course_not_in_group")));
        if (errorCode == "grading_element_not_found")
            return NotFound(new ErrorEnvelope(new ApiError("grading_element_not_found")));
        if (errorCode == "cumulative_grade_not_configured")
            return NotFound(new ErrorEnvelope(new ApiError("cumulative_grade_not_configured")));
        return BadRequest(new ErrorEnvelope(new ApiError(errorCode)));
    }

    private Guid? GetCurrentUserId()
    {
        var sub = User.FindFirst(ClaimTypes.NameIdentifier)?.Value
                  ?? User.FindFirst("sub")?.Value;
        return Guid.TryParse(sub, out var id) ? id : null;
    }
}
