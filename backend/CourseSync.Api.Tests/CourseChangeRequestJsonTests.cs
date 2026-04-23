using System.Text.Json;
using CourseSync.Api.Application.Courses;
using CourseSync.Api.Models;
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
            {"name":"математические методы","general_info":"string","contacts":[{"name":"Иванов Иван Иванович","contact_methods":[{"type":"Почта","value":"teacher@example.com"},{"type":"","value":"+70000000000"}]}],"useful_links":[{"title":"Книга","url":"https://example.com"}]}
            """;

        var req = JsonSerializer.Deserialize<ChangeCourseRequest>(json, Options);
        Assert.NotNull(req);
        Assert.Equal("математические методы", req.Name);
        Assert.Equal("string", req.GeneralInfo);
        var contacts = Assert.IsAssignableFrom<IReadOnlyList<CourseContactPersonItem>>(req.Contacts);
        Assert.Single(contacts);
        Assert.Equal("Иванов Иван Иванович", contacts[0].Name);
        var methods = Assert.IsAssignableFrom<IReadOnlyList<CourseContactMethodItem>>(contacts[0].ContactMethods);
        Assert.Equal(2, methods.Count);
        Assert.Equal("Почта", methods[0].Type);
        Assert.Equal("teacher@example.com", methods[0].Value);
        Assert.Equal("", methods[1].Type);
        Assert.Equal("+70000000000", methods[1].Value);
        Assert.NotNull(req.UsefulLinks);
        Assert.Single(req.UsefulLinks);
        Assert.Equal("Книга", req.UsefulLinks[0].Title);
        Assert.Equal("https://example.com", req.UsefulLinks[0].Url);

        var v = CourseInputRules.ValidateCourseName(req.Name);
        Assert.True(v.Valid);
        Assert.Null(v.ErrorCode);
    }

    [Fact]
    public void ChangeCourse_deserializes_when_contacts_omitted()
    {
        const string json = """{"name":"название курса","general_info":"","useful_links":[]}""";
        var req = JsonSerializer.Deserialize<ChangeCourseRequest>(json, Options);
        Assert.NotNull(req);
        Assert.Null(req!.Contacts);
    }
}
