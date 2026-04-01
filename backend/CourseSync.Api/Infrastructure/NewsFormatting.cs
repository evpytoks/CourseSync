using System.Globalization;

namespace CourseSync.Api.Infrastructure;

public static class NewsFormatting
{
    public const string SectionGroups = "Группы";
    public const string SectionCourses = "Курсы";
    public const string SectionCalendar = "Календарь";
    public const string SectionNews = "Новости";

    private static string Q(string? s)
    {
        var t = Trim(s ?? "");
        return string.IsNullOrEmpty(t) ? "«»" : $"«{t}»";
    }

    public static string SingleState(string label, string value) => $"{FieldTitle(label)}\n{FormatDiffBlock(value)}";

    public static string WasOnly(string label, string value) =>
        $"{FieldTitle(label)}\n{FormatDiffBlock(value)}";

    public static string BeforeAfter(string label, string before, string after) =>
        $"{FieldTitle(label)}\n\n" +
        "Раньше\n" +
        $"{FormatDiffBlock(before)}\n\n" +
        "Теперь\n" +
        FormatDiffBlock(after);

    public static string? BuildChangedCourseFields(
        string oldName,
        string newName,
        string oldGeneral,
        string newGeneral,
        string oldLinks,
        string newLinks)
    {
        var lines = new List<string>();
        if (Trim(oldName) != Trim(newName))
            lines.Add(BeforeAfter("название", Trim(oldName), Trim(newName)));
        if (Trim(oldGeneral) != Trim(newGeneral))
            lines.Add(BeforeAfter("общая информация", Trim(oldGeneral), Trim(newGeneral)));
        if (Trim(oldLinks) != Trim(newLinks))
            lines.Add(BeforeAfter("полезные ссылки", Trim(oldLinks), Trim(newLinks)));
        return lines.Count == 0 ? null : string.Join("\n\n", lines);
    }

    public static string? BuildChangedCourseFieldsGeneralLinks(
        string oldGeneral,
        string newGeneral,
        string oldLinksRaw,
        string newLinksRaw)
    {
        var lines = new List<string>();
        if (Trim(oldGeneral) != Trim(newGeneral))
            lines.Add(BeforeAfter("общая информация", Trim(oldGeneral), Trim(newGeneral)));
        if (Trim(oldLinksRaw) != Trim(newLinksRaw))
            lines.Add(BeforeAfter(
                "полезные ссылки",
                UsefulLinksCodec.FormatForDisplay(oldLinksRaw),
                UsefulLinksCodec.FormatForDisplay(newLinksRaw)));
        return lines.Count == 0 ? null : string.Join("\n\n", lines);
    }

    private static string Trim(string s) => (s ?? "").Trim();

    private static string FieldTitle(string label)
    {
        var t = Trim(label);
        if (t.Length == 0) return t;
        return char.ToUpperInvariant(t[0]) + (t.Length > 1 ? t[1..] : "");
    }

    private static string FormatDiffBlock(string? s)
    {
        var t = Trim(s ?? "");
        if (string.IsNullOrEmpty(t)) return "—";
        if (t.Contains('\n', StringComparison.Ordinal) || t.Length > 100)
            return t;
        return Q(t);
    }

    public static string FormatGradingFormulaLines(IReadOnlyList<(string Name, decimal Coefficient)> elements, string gradingText)
    {
        var parts = new List<string>();
        if (elements.Count > 0)
        {
            var el = string.Join(
                "; ",
                elements.Select(e => $"{Q(e.Name)} ({FormatCoeff(e.Coefficient)})"));
            parts.Add($"Элементы: {el}");
        }
        else
            parts.Add("Элементы: (нет)");

        var t = (gradingText ?? "").Trim();
        parts.Add(t.Length == 0 ? "Пояснение к формуле: (пусто)" : $"Пояснение к формуле: {Q(t)}");
        return string.Join("\n", parts);
    }

    private static string FormatCoeff(decimal c) => c.ToString("0.####", System.Globalization.CultureInfo.InvariantCulture);

    public static string DetailGroupRenamed(string oldName, string newName) =>
        $"Группа {Q(oldName)} переименована в {Q(newName)}";

    public static string DetailMemberJoinedByCode(string participantEmail) =>
        $"Присоединился новый участник {Trim(participantEmail)}.";

    public static string DetailCourseCreatedInGroup(string courseName) =>
        $"Добавлен новый курс {Q(courseName)}.";

    public static string DetailCourseRenamedInGroup(string oldCourseName, string newCourseName) =>
        $"Курс {Q(oldCourseName)} переименован на {Q(newCourseName)}.";

    public static string DetailCourseFieldsUpdatedInGroup(string courseName, string changedGeneralLinks) =>
        $"У курса {Q(courseName)} изменены сведения о курсе.\n\n{changedGeneralLinks}";

    public static string DetailCourseDeleted(string courseName) =>
        "Курс убрали из списка.\n\n" +
        WasOnly("Название", courseName);

    public static string DetailGradingChangedInGroup(string courseName) =>
        $"У курса {Q(courseName)} изменена формула оценивания.";

    public static string DetailCalendarEventCreated(string eventName, DateTime dateUtc, string description) =>
        $"Добавили событие.\n\n{FormatCalendarEventSnapshot(eventName, dateUtc, description)}";

    public static string DetailCalendarEventUpdated(
        string newName,
        DateTime newDateUtc,
        string newDescription,
        string oldName,
        DateTime oldDateUtc,
        string oldDescription) =>
        "Изменили событие.\n\n" +
        "Сейчас\n" +
        $"{FormatCalendarEventSnapshot(newName, newDateUtc, newDescription)}\n\n" +
        "Раньше\n" +
        FormatCalendarEventSnapshot(oldName, oldDateUtc, oldDescription);

    public static string DetailCalendarEventDeleted(string eventName, DateTime dateUtc, string description) =>
        $"Удалили событие.\n\n{FormatCalendarEventSnapshot(eventName, dateUtc, description)}";

    private static string FormatCalendarEventSnapshot(string eventName, DateTime dateUtc, string? description)
    {
        var desc = Trim(description ?? "");
        return
            $"Название: {Q(eventName)}\n" +
            $"Дата и время: {FormatCalendarDateTime(dateUtc)}\n" +
            $"Описание: {CalendarDescriptionLine(desc)}";
    }

    private static string CalendarDescriptionLine(string trimmedDescription) =>
        trimmedDescription.Length == 0 ? "без описания" : Q(trimmedDescription);

    private static string FormatCalendarDateTime(DateTime dateUtc) =>
        dateUtc.ToString("dd.MM.yyyy HH:mm", CultureInfo.InvariantCulture);

    public static string DetailGeneralMaterialAdded(string courseName, string fileName) =>
        $"В курсе {Q(courseName)} появился новый материал {Trim(fileName)}";

    public static string DetailPersonalMaterialAdded(string courseName, string authorEmail, string fileName) =>
        $"В курсе {Q(courseName)} {Trim(authorEmail)} загрузил новый материал {Trim(fileName)}";
}
