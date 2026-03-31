using System.Globalization;

namespace CourseSync.Api.Infrastructure;

public static class NewsFormatting
{
    public const string SectionGroups = "Группы";
    public const string SectionCourses = "Курсы";
    public const string SectionCalendar = "Календарь";
    public const string SectionNews = "Новости";

    public static string SingleState(string label, string value) => $"{label}: {value}";

    public static string WasOnly(string label, string value) => $"Было — {label}: {value}";

    public static string BeforeAfter(string label, string before, string after) =>
        $"Было — {label}: {before}\nСтало — {label}: {after}";

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
        string oldLinks,
        string newLinks)
    {
        var lines = new List<string>();
        if (Trim(oldGeneral) != Trim(newGeneral))
            lines.Add(BeforeAfter("общая информация", Trim(oldGeneral), Trim(newGeneral)));
        if (Trim(oldLinks) != Trim(newLinks))
            lines.Add(BeforeAfter("полезные ссылки", Trim(oldLinks), Trim(newLinks)));
        return lines.Count == 0 ? null : string.Join("\n\n", lines);
    }

    private static string Trim(string s) => (s ?? "").Trim();

    public static string FormatGradingFormulaLines(IReadOnlyList<(string Name, decimal Coefficient)> elements, string gradingText)
    {
        var parts = new List<string>();
        if (elements.Count > 0)
        {
            var el = string.Join(
                "; ",
                elements.Select(e => $"{e.Name.Trim()} ({FormatCoeff(e.Coefficient)})"));
            parts.Add($"Элементы: {el}");
        }
        else
            parts.Add("Элементы: (нет)");

        var t = (gradingText ?? "").Trim();
        parts.Add(t.Length == 0 ? "Пояснение к формуле: (пусто)" : $"Пояснение к формуле: {t}");
        return string.Join("\n", parts);
    }

    private static string FormatCoeff(decimal c) => c.ToString("0.####", System.Globalization.CultureInfo.InvariantCulture);

    public static string DetailGroupRenamed(string oldName, string newName) =>
        $"Группа {Trim(oldName)} переименована в {Trim(newName)}";

    public static string DetailMemberJoinedByCode(string groupName, string participantEmail) =>
        $"К группе {Trim(groupName)} присоединился новый участник {Trim(participantEmail)}";

    public static string DetailCourseCreatedInGroup(string groupName, string courseName) =>
        $"В группу {Trim(groupName)} добавлен новый курс {Trim(courseName)}";

    public static string DetailCourseRenamedInGroup(string groupName, string oldCourseName, string newCourseName) =>
        $"В группе {Trim(groupName)} курс {Trim(oldCourseName)} переименован на {Trim(newCourseName)}";

    public static string DetailCourseFieldsUpdatedInGroup(string groupName, string courseName, string changedGeneralLinks) =>
        $"В группе {Trim(groupName)} у курса {Trim(courseName)} изменены сведения о курсе.\n\n{changedGeneralLinks}";

    public static string DetailCourseDeleted(string courseName) =>
        "Курс убрали из списка группы.\n\n" +
        WasOnly("Название", courseName);

    public static string DetailGradingChangedInGroup(string groupName, string courseName) =>
        $"В группе {Trim(groupName)} у курса {Trim(courseName)} изменена формула оценивания";

    public static string DetailCalendarEventCreated(string groupName, string eventName, DateTime dateUtc, string description)
    {
        var g = Trim(groupName);
        var n = Trim(eventName);
        var d = FormatCalendarDateTime(dateUtc);
        var desc = Trim(description ?? "");
        return $"В группе {g} добавлено событие {n}\nДата:{d}\nОписание:{desc}";
    }

    public static string DetailCalendarEventUpdated(
        string groupName,
        string newName,
        DateTime newDateUtc,
        string newDescription,
        string oldName,
        DateTime oldDateUtc,
        string oldDescription)
    {
        var g = Trim(groupName);
        var newBlock =
            $"{Trim(newName)}\nДата:{FormatCalendarDateTime(newDateUtc)}\nОписание:{Trim(newDescription ?? "")}";
        var oldBlock =
            $"{Trim(oldName)}\nДата:{FormatCalendarDateTime(oldDateUtc)}\nОписание:{Trim(oldDescription ?? "")}";
        return $"В группе {g} изменено событие \n\n{newBlock}\n\nСтарая версия:\n{oldBlock}";
    }

    public static string DetailCalendarEventDeleted(string groupName, string eventName, DateTime dateUtc, string description)
    {
        var g = Trim(groupName);
        var n = Trim(eventName);
        var d = FormatCalendarDateTime(dateUtc);
        var desc = Trim(description ?? "");
        return $"В группе {g} удалено событие {n}\nДата:{d}\nОписание:{desc}";
    }

    private static string FormatCalendarDateTime(DateTime dateUtc) =>
        dateUtc.ToString("dd.MM.yyyy HH:mm", CultureInfo.InvariantCulture);

    public static string DetailGeneralMaterialAdded(string courseName, string fileName) =>
        $"В курсе {Trim(courseName)} появился новый материал {Trim(fileName)}";

    public static string DetailPersonalMaterialAdded(string courseName, string authorEmail, string fileName) =>
        $"В курсе {Trim(courseName)} {Trim(authorEmail)} загрузил новый материал {Trim(fileName)}";
}
