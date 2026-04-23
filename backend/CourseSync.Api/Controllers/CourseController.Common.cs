using System;
using System.Collections.Generic;
using CourseSync.Api.Data;
using CourseSync.Api.Models;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

public sealed partial class CourseController
{
    private static readonly IReadOnlyDictionary<string, ErrorSpec> ForbiddenOnlyErrors =
        new Dictionary<string, ErrorSpec>(StringComparer.Ordinal)
        {
            ["forbidden"] = new(403, "forbidden")
        };

    private static readonly IReadOnlyDictionary<string, ErrorSpec> MaterialAccessErrors =
        new Dictionary<string, ErrorSpec>(StringComparer.Ordinal)
        {
            ["forbidden"] = new(403, "forbidden")
        };

    private static readonly IReadOnlyDictionary<string, ErrorSpec> AddMaterialErrors =
        new Dictionary<string, ErrorSpec>(StringComparer.Ordinal)
        {
            ["forbidden"] = new(403, "forbidden"),
            ["course_not_in_group"] = new(400, "course_not_in_group")
        };

    private static readonly IReadOnlyDictionary<string, ErrorSpec> DeleteMaterialErrors =
        new Dictionary<string, ErrorSpec>(StringComparer.Ordinal)
        {
            ["forbidden"] = new(403, "forbidden"),
            ["course_not_in_group"] = new(400, "course_not_in_group"),
            ["material_not_found"] = new(404, "material_not_found"),
            ["storage_delete_failed"] = new(503, "storage_delete_failed")
        };

    private static readonly IReadOnlyDictionary<string, ErrorSpec> DownloadMaterialErrors =
        new Dictionary<string, ErrorSpec>(StringComparer.Ordinal)
        {
            ["forbidden"] = new(403, "forbidden"),
            ["course_not_in_group"] = new(400, "course_not_in_group"),
            ["material_not_found"] = new(404, "material_not_found")
        };

    private static readonly IReadOnlyDictionary<string, ErrorSpec> GradingErrors =
        new Dictionary<string, ErrorSpec>(StringComparer.Ordinal)
        {
            ["forbidden"] = new(403, "forbidden"),
            ["course_not_in_group"] = new(400, "course_not_in_group"),
            ["grading_element_not_found"] = new(404, "grading_element_not_found"),
            ["cumulative_grade_not_configured"] = new(404, "cumulative_grade_not_configured")
        };

    private async Task<(ActionResult? Error, Guid UserId, User User)> TryResolveAuthenticatedUserAsync(CancellationToken ct)
    {
        var (userId, user, error) = await ResolveUserOrUnauthorizedAsync(_userService, ct);
        if (error is not null || user is null)
            return (error, default, null!);

        return (null, userId, user);
    }

    private ActionResult? RequireSelectedGroup(User user, out Guid groupId)
    {
        var (resolvedGroupId, error) = ResolveSelectedGroupOrError(user);
        groupId = resolvedGroupId;
        return error;
    }

    private ActionResult MaterialAccessError(string errorCode)
        => MapError(errorCode, MaterialAccessErrors, "course_not_in_group");

    private ActionResult AddMaterialError(string errorCode)
        => MapError(errorCode, AddMaterialErrors);

    private ActionResult DeleteMaterialError(string errorCode)
        => MapError(errorCode, DeleteMaterialErrors);

    private ActionResult DownloadMaterialError(string errorCode)
        => MapError(errorCode, DownloadMaterialErrors);

    private ActionResult GradingError(string errorCode)
        => MapError(errorCode, GradingErrors);

    private ActionResult ForbiddenOnlyError(string errorCode)
        => MapError(errorCode, ForbiddenOnlyErrors);

}
