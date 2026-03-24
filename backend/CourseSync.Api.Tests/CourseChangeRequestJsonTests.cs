using System.Text.Json;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class CourseChangeRequestJsonTests
{
    private static readonly JsonSerializerOptions Options = new()
    {
        PropertyNameCaseInsensitive = true
    };

    [Fact]
    public void ChangeCourse_body_like_client_deserializes_and_name_passes_validation()
    {
        const string json = """
            {"name":"математические методы","general_info":"string","useful_links":"string"}
            """;

        var req = JsonSerializer.Deserialize<ChangeCourseRequest>(json, Options);
        Assert.NotNull(req);
        Assert.Equal("математические методы", req.Name);
        Assert.Equal("string", req.GeneralInfo);
        Assert.Equal("string", req.UsefulLinks);

        var v = CourseService.ValidateCourseName(req.Name);
        Assert.True(v.Valid);
        Assert.Null(v.ErrorCode);
    }
}
