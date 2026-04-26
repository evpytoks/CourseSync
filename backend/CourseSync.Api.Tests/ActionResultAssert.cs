using Microsoft.AspNetCore.Mvc;
using Xunit;

namespace CourseSync.Api.Tests;

internal static class ActionResultAssert
{
    public static ObjectResult Status(IActionResult? result, int expectedStatusCode)
    {
        Assert.NotNull(result);
        var obj = Assert.IsAssignableFrom<ObjectResult>(result);
        Assert.Equal(expectedStatusCode, obj.StatusCode);
        return obj;
    }

    public static ObjectResult Unauthorized(IActionResult? result) => Status(result, 401);

    public static ObjectResult BadRequest(IActionResult? result) => Status(result, 400);

    public static ObjectResult NotFound(IActionResult? result) => Status(result, 404);
}
