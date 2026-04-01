using System.Linq;
using System.Text.Json;
using CourseSync.Api.Models;

namespace CourseSync.Api.Infrastructure;

public static class UsefulLinksCodec
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        WriteIndented = false
    };

    public static string ToStorage(IReadOnlyList<CourseUsefulLinkItem> items)
    {
        var list = items ?? Array.Empty<CourseUsefulLinkItem>();
        return JsonSerializer.Serialize(list, JsonOptions);
    }

    public static IReadOnlyList<CourseUsefulLinkItem> FromStorage(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw))
            return Array.Empty<CourseUsefulLinkItem>();

        var t = raw.Trim();
        if (t.StartsWith('['))
        {
            try
            {
                var list = JsonSerializer.Deserialize<List<CourseUsefulLinkItem>>(t, JsonOptions);
                if (list is null)
                    return Array.Empty<CourseUsefulLinkItem>();
                return list;
            }
            catch (JsonException)
            {
                return LegacyFallback(t);
            }
        }

        return LegacyFallback(t);
    }

    private const int UrlMaxLength = 200;

    private static IReadOnlyList<CourseUsefulLinkItem> LegacyFallback(string raw)
    {
        var url = raw.Length > UrlMaxLength ? raw[..UrlMaxLength] : raw;
        return new[] { new CourseUsefulLinkItem("Ссылка", url.Trim()) };
    }

    public static string FormatForDisplay(string? raw)
    {
        var items = FromStorage(raw);
        if (items.Count == 0)
            return "—";
        return string.Join(
            "\n",
            items.Select(i => $"{i.Title} — {i.Url}"));
    }
}
