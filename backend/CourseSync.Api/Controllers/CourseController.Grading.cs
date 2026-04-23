using CourseSync.Api.Application.Courses;
using CourseSync.Api.Infrastructure;
using CourseSync.Api.Models;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

public sealed partial class CourseController
{
    [HttpPost("{id:guid}/grading")]
    public async Task<IActionResult> SaveGrading(Guid id, [FromBody] SaveCourseGradingRequest req, CancellationToken ct)
    {
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        if (req is null)
            return BadRequest(new ErrorEnvelope(new ApiError("invalid_request")));
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var elements = (req.Elements ?? Array.Empty<CourseGradingElementRequest>())
            .Select(e => (e.Name ?? "", e.Coefficient, e.Block ?? 0m))
            .ToList();

        var (ok, errorCode) = await _courseGrading.SaveGradingAsync(
            userId,
            groupId,
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
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var (ok, text, errorCode) = await _courseGrading.GetGradingTextAsync(userId, groupId, id, ct);

        if (!ok)
            return GradingError(errorCode!);

        return Ok(new CourseGradingTextResponse(text ?? ""));
    }

    [HttpGet("{id:guid}/grading/elements")]
    public async Task<IActionResult> GradingElements(Guid id, CancellationToken ct)
    {
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var (ok, items, errorCode) = await _courseGrading.GetGradingElementOptionsAsync(userId, groupId, id, ct);

        if (!ok)
            return GradingError(errorCode!);

        var list = items!.Select(x => new CourseGradingElementOptionItem(x.Id, x.Name)).ToList();
        return Ok(new CourseGradingElementListResponse(list));
    }

    [HttpGet("{id:guid}/grading")]
    public async Task<IActionResult> GetGrading(Guid id, CancellationToken ct)
    {
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var (ok, elements, errorCode) = await _courseGrading.GetGradingAsync(userId, groupId, id, ct);

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
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var trimmed = (name ?? "").Trim();
        if (trimmed.Length == 0)
        {
            var (allOk, allElements, allError) = await _courseGrading.GetAllGradingScoresAsync(userId, groupId, id, ct);
            if (!allOk)
                return GradingError(allError!);
            var blocks = (allElements ?? Array.Empty<GradingElementScoresRow>())
                .Select(e => new CourseGradingScoresResponse(e.Name, e.Count, e.Scores))
                .ToList();
            return Ok(new CourseGradingAllScoresResponse(blocks));
        }

        var (ok, elementName, count, scores, errorCode) = await _courseGrading.GetGradingScoresAsync(
            userId,
            groupId,
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
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        if (req is null)
            return BadRequest(new ErrorEnvelope(new ApiError("grading_element_name_required")));
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var scores = req.Scores ?? Array.Empty<decimal>();
        var (ok, errorCode) = await _courseGrading.UpdateGradingScoresAsync(
            userId,
            groupId,
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
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        if (req is null)
            return BadRequest(new ErrorEnvelope(new ApiError("cumulative_grade_elements_required")));
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var (ok, errorCode) = await _courseCumulativeGrade.SaveCumulativeGradeAsync(
            userId,
            groupId,
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
        var (authErr, userId, user) = await TryResolveAuthenticatedUserAsync(ct);
        if (authErr is not null)
            return authErr;
        var groupErr = RequireSelectedGroup(user, out var groupId);
        if (groupErr is not null)
            return groupErr;

        var (ok, data, errorCode) = await _courseCumulativeGrade.GetCumulativeGradeAsync(userId, groupId, id, ct);

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
}
