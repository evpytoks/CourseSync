using System.Text.Json;
using CourseSync.Api.Models;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class ApiErrorJsonTests
{
    [Fact]
    public void ErrorEnvelope_serializes_only_error_code()
    {
        var json = JsonSerializer.Serialize(
            new ErrorEnvelope(new ApiError("invalid_code")),
            new JsonSerializerOptions(JsonSerializerDefaults.Web));

        using var doc = JsonDocument.Parse(json);

        var error = doc.RootElement.GetProperty("error");
        Assert.Equal("invalid_code", error.GetProperty("error_code").GetString());
    }
}
