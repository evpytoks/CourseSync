using System.Security.Claims;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

[ApiController]
[Route("news")]
[Authorize]
[Produces("application/json")]
public sealed class NewsController : ControllerBase
{
    private readonly NewsService _newsService;
    private readonly UserService _userService;

    public NewsController(NewsService newsService, UserService userService)
    {
        _newsService = newsService;
        _userService = userService;
    }

    [HttpGet]
    public async Task<ActionResult<NewsListResponse>> List(CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var items = await _newsService.GetAllForUserAsync(userId.Value, ct);

        var news = items
            .Select(n => new NewsListItem(n.Id, n.Time, n.Group, n.Section, n.Text))
            .ToList();

        return Ok(new NewsListResponse(news));
    }

    [HttpGet("{id:guid}")]
    public async Task<ActionResult<NewsDetailsResponse>> Get(Guid id, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var (ok, item, errorCode) = await _newsService.GetByIdAsync(
            userId.Value,
            id,
            ct);

        if (!ok)
        {
            if (errorCode == "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
            if (errorCode == "news_not_found")
                return NotFound(new ErrorEnvelope(new ApiError("news_not_found")));
            return BadRequest(new ErrorEnvelope(new ApiError(errorCode!)));
        }

        return Ok(new NewsDetailsResponse(item!.Id, item.Time, item.Group, item.Section, item.Text));
    }

    [HttpPost("add")]
    public async Task<IActionResult> Add([FromBody] AddNewsRequest req, CancellationToken ct)
    {
        var userId = GetCurrentUserId();
        if (userId is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        var user = await _userService.FindByIdAsync(userId.Value, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("unauthorized")));

        if (req.GroupId == Guid.Empty)
            return BadRequest(new ErrorEnvelope(new ApiError("group_id_required")));

        var textValidation = NewsService.ValidateNewsText(req.Text);
        if (!textValidation.Valid)
            return BadRequest(new ErrorEnvelope(new ApiError(textValidation.ErrorCode!)));

        var (ok, errorCode) = await _newsService.CheckOwnerAndCreateNewsAsync(
            userId.Value,
            req.GroupId,
            req.Text!.Trim(),
            ct);

        if (!ok)
        {
            if (errorCode == "forbidden")
                return StatusCode(403, new ErrorEnvelope(new ApiError("forbidden")));
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
