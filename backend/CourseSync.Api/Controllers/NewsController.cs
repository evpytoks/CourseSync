using System;
using System.Collections.Generic;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

[ApiController]
[Route("news")]
[Authorize]
[Produces("application/json")]
public sealed class NewsController : AuthorizedControllerBase
{
    private static readonly IReadOnlyDictionary<string, ErrorSpec> NewsAccessErrors =
        new Dictionary<string, ErrorSpec>(StringComparer.Ordinal)
        {
            ["forbidden"] = new(403, "forbidden"),
            ["news_not_found"] = new(404, "news_not_found")
        };

    private static readonly IReadOnlyDictionary<string, ErrorSpec> NewsCreateErrors =
        new Dictionary<string, ErrorSpec>(StringComparer.Ordinal)
        {
            ["forbidden"] = new(403, "forbidden")
        };

    private readonly INewsService _newsService;
    private readonly IUserService _userService;

    public NewsController(INewsService newsService, IUserService userService)
    {
        _newsService = newsService;
        _userService = userService;
    }

    [HttpGet]
    public async Task<ActionResult<NewsListResponse>> List(CancellationToken ct)
    {
        var (userId, authError) = await TryResolveAuthenticatedUserAsync(ct);
        if (authError is not null)
            return authError;

        var items = await _newsService.GetAllForUserAsync(userId, ct);

        var news = items
            .Select(n => new NewsListItem(n.Id, n.Time, n.Group, n.Section, n.Text, n.IsRead))
            .ToList();

        return Ok(new NewsListResponse(news));
    }

    [HttpGet("unread-count")]
    public async Task<ActionResult<NewsUnreadCountResponse>> UnreadCount(CancellationToken ct)
    {
        var (userId, authError) = await TryResolveAuthenticatedUserAsync(ct);
        if (authError is not null)
            return authError;

        var count = await _newsService.GetUnreadCountAsync(userId, ct);
        return Ok(new NewsUnreadCountResponse(count));
    }

    [HttpPost("read")]
    public async Task<ActionResult<MarkAllNewsReadResponse>> MarkAllRead(CancellationToken ct)
    {
        var (userId, authError) = await TryResolveAuthenticatedUserAsync(ct);
        if (authError is not null)
            return authError;

        var marked = await _newsService.MarkAllReadAsync(userId, ct);
        return Ok(new MarkAllNewsReadResponse(marked));
    }

    [HttpGet("{id:guid}")]
    public async Task<ActionResult<NewsDetailsResponse>> Get(Guid id, CancellationToken ct)
    {
        var (userId, authError) = await TryResolveAuthenticatedUserAsync(ct);
        if (authError is not null)
            return authError;

        var (ok, item, errorCode) = await _newsService.GetByIdAsync(
            userId,
            id,
            ct);

        if (!ok)
            return MapError(errorCode, NewsAccessErrors);

        return Ok(new NewsDetailsResponse(item!.Id, item.Time, item.Group, item.Section, item.Text));
    }

    [HttpPost]
    public async Task<IActionResult> Add([FromBody] AddNewsRequest req, CancellationToken ct)
    {
        var (userId, authError) = await TryResolveAuthenticatedUserAsync(ct);
        if (authError is not null)
            return authError;

        if (req is null)
            return ErrorResponse("group_id_required");

        if (req.GroupId == Guid.Empty)
            return ErrorResponse("group_id_required");

        var textValidation = NewsService.ValidateNewsText(req.Text);
        if (!textValidation.Valid)
            return ErrorResponse(textValidation.ErrorCode!);

        var (ok, errorCode) = await _newsService.CheckOwnerAndCreateNewsAsync(
            userId,
            req.GroupId,
            req.Text!.Trim(),
            ct);

        if (!ok)
            return MapError(errorCode, NewsCreateErrors);

        return Ok();
    }

    private async Task<(Guid UserId, ActionResult? Error)> TryResolveAuthenticatedUserAsync(CancellationToken ct)
    {
        var (userId, _, error) = await ResolveUserOrUnauthorizedAsync(_userService, ct);
        return (userId, error);
    }
}
