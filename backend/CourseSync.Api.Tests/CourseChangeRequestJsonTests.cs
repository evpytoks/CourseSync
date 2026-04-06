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
            {"name":"математические методы","general_info":"string","contacts":"tg: @t","useful_links":[{"title":"Книга","url":"https://example.com"}]}
            """;

        var req = JsonSerializer.Deserialize<ChangeCourseRequest>(json, Options);
        Assert.NotNull(req);
        Assert.Equal("математические методы", req.Name);
        Assert.Equal("string", req.GeneralInfo);
        Assert.Equal("tg: @t", req.Contacts);
        Assert.NotNull(req.UsefulLinks);
        Assert.Single(req.UsefulLinks);
        Assert.Equal("Книга", req.UsefulLinks[0].Title);
        Assert.Equal("https://example.com", req.UsefulLinks[0].Url);

        var v = CourseService.ValidateCourseName(req.Name);
        Assert.True(v.Valid);
        Assert.Null(v.ErrorCode);
    }

    [Fact]
    public void ChangeCourse_deserializes_when_contacts_omitted()
    {
        const string json = """{"name":"a","general_info":"","useful_links":[]}""";
        var req = JsonSerializer.Deserialize<ChangeCourseRequest>(json, Options);
        Assert.NotNull(req);
        Assert.Null(req!.Contacts);
    }
}
