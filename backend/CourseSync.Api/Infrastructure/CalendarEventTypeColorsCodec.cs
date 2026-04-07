using System.Text.Json;

namespace CourseSync.Api.Infrastructure;

public static class CalendarEventTypeColorsCodec
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        WriteIndented = false
    };

    public static IReadOnlyDictionary<string, string> FromStorage(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw))
            return new Dictionary<string, string>(StringComparer.Ordinal);

        try
        {
            var dict = JsonSerializer.Deserialize<Dictionary<string, string>>(raw, JsonOptions);
            if (dict is null)
                return new Dictionary<string, string>(StringComparer.Ordinal);

            return dict;
        }
        catch (JsonException)
        {
            return new Dictionary<string, string>(StringComparer.Ordinal);
        }
    }

    public static string ToStorage(IReadOnlyDictionary<string, string> colors)
    {
        var dict = new Dictionary<string, string>(StringComparer.Ordinal);
        foreach (var pair in colors)
            dict[pair.Key] = pair.Value;
        return JsonSerializer.Serialize(dict, JsonOptions);
    }
}
