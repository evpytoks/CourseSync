using System.Linq;
using System.Text.Json;
using CourseSync.Api.Models;

namespace CourseSync.Api.Infrastructure;

public static class ContactsCodec
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        WriteIndented = false
    };

    public static string ToStorage(IReadOnlyList<CourseContactPersonItem> people)
    {
        var list = people ?? Array.Empty<CourseContactPersonItem>();
        return JsonSerializer.Serialize(list, JsonOptions);
    }

    public static IReadOnlyList<CourseContactPersonItem> FromStorage(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw))
            return Array.Empty<CourseContactPersonItem>();

        var t = raw.Trim();
        if (t.StartsWith('['))
        {
            try
            {
                var list = JsonSerializer.Deserialize<List<CourseContactPersonItem>>(t, JsonOptions);
                if (list is null)
                    return Array.Empty<CourseContactPersonItem>();
                return list;
            }
            catch (JsonException)
            {
                return LegacyFallback(t);
            }
        }

        return LegacyFallback(t);
    }

    private static IReadOnlyList<CourseContactPersonItem> LegacyFallback(string raw) =>
        raw.Length == 0
            ? Array.Empty<CourseContactPersonItem>()
            : new[]
            {
                new CourseContactPersonItem(
                    Name: raw.Trim(),
                    ContactMethods: Array.Empty<CourseContactMethodItem>())
            };

    public static string FormatForDisplay(string? raw)
    {
        var people = FromStorage(raw);
        if (people.Count == 0)
            return "—";

        return string.Join(
            "\n",
            people.Select(p =>
            {
                var name = (p.Name ?? "").Trim();
                var methods = p.ContactMethods ?? Array.Empty<CourseContactMethodItem>();
                if (methods.Count == 0)
                    return name.Length == 0 ? "—" : name;

                var links = string.Join(
                    "; ",
                    methods.Select(m =>
                    {
                        var type = (m.Type ?? "").Trim();
                        var value = (m.Value ?? "").Trim();
                        return type.Length == 0 ? value : $"{type}: {value}";
                    }));
                return name.Length == 0 ? links : $"{name} — {links}";
            }));
    }
}
