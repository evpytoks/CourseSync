namespace CourseSync.Api.Infrastructure;

public static class CalendarEventCatalog
{
    public const string DefaultColor = "#2563EB";

    private static readonly string[] Types =
    {
        "Тест",
        "СР",
        "КР",
        "ДЗ",
        "Коллоквиум",
        "Экзамен",
        "Консультация",
        "Защита",
        "Другое"
    };

    public static IReadOnlyList<string> GetTypes() => Types;

    public static bool TryNormalizeType(string? raw, out string normalized)
    {
        var value = (raw ?? "").Trim();
        foreach (var t in Types)
        {
            if (string.Equals(t, value, StringComparison.OrdinalIgnoreCase))
            {
                normalized = t;
                return true;
            }
        }

        normalized = "";
        return false;
    }
}
