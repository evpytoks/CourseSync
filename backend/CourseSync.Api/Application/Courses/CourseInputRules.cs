using CourseSync.Api.Infrastructure;
using CourseSync.Api.Models;

namespace CourseSync.Api.Application.Courses;

public static class CourseInputRules
{
    public const int CourseNameMaxLength = 50;

    private const int GeneralInfoMaxLength = 2000;
    private const int ContactsMaxLength = 2000;
    public const int ContactsMaxPeople = 10;
    public const int ContactMethodsMaxPerPerson = 10;

    public const int UsefulLinkTitleMinLength = 1;
    public const int UsefulLinkTitleMaxLength = 50;
    public const int UsefulLinkUrlMinLength = 1;
    public const int UsefulLinkUrlMaxLength = 200;
    public const int UsefulLinksMaxItems = 50;
    private const int UsefulLinksStorageMaxLength = 8000;

    public static (bool Valid, string? ErrorCode) ValidateCourseName(string? name)
    {
        if (string.IsNullOrWhiteSpace(name))
            return (false, "course_name_required");
        name = name.Trim();
        if (name.Length > CourseNameMaxLength)
            return (false, "course_name_too_long");
        return (true, null);
    }

    public static (bool Valid, string? ErrorCode) ValidateGeneralInfo(string? value)
    {
        if (value is null)
            return (true, null);
        if (value.Length > GeneralInfoMaxLength)
            return (false, "general_info_too_long");
        return (true, null);
    }

    public static (bool Valid, string? ErrorCode) ValidateContacts(IReadOnlyList<CourseContactPersonItem>? people)
    {
        if (people is null)
            return (false, "contacts_required");

        if (people.Count > ContactsMaxPeople)
            return (false, "contacts_too_many_people");

        foreach (var person in people)
        {
            var personName = (person.Name ?? "").Trim();
            if (personName.Length == 0)
                return (false, "contact_person_name_required");

            var methods = person.ContactMethods;
            if (methods is null)
                return (false, "contact_methods_required");
            if (methods.Count > ContactMethodsMaxPerPerson)
                return (false, "contact_methods_too_many");

            foreach (var method in methods)
            {
                if (method.Type is null)
                    return (false, "contact_method_type_required");
                var value = (method.Value ?? "").Trim();
                if (value.Length == 0)
                    return (false, "contact_method_value_required");
            }
        }

        var storage = ContactsCodec.ToStorage(people);
        if (storage.Length > ContactsMaxLength)
            return (false, "contacts_too_long");

        return (true, null);
    }

    public static (bool Valid, string? ErrorCode) ValidateUsefulLinks(IReadOnlyList<CourseUsefulLinkItem>? items)
    {
        if (items is null)
            return (true, null);
        if (items.Count > UsefulLinksMaxItems)
            return (false, "useful_links_too_many");

        foreach (var item in items)
        {
            var title = (item.Title ?? "").Trim();
            var url = (item.Url ?? "").Trim();
            if (url.Length < UsefulLinkUrlMinLength || url.Length > UsefulLinkUrlMaxLength)
                return (false, "useful_link_url_invalid");
            if (title.Length > 0 && (title.Length < UsefulLinkTitleMinLength || title.Length > UsefulLinkTitleMaxLength))
                return (false, "useful_link_title_invalid");
        }

        var storage = UsefulLinksCodec.ToStorage(NormalizeUsefulLinks(items));
        if (storage.Length > UsefulLinksStorageMaxLength)
            return (false, "useful_links_too_long");
        return (true, null);
    }

    public static IReadOnlyList<CourseUsefulLinkItem> NormalizeUsefulLinks(IReadOnlyList<CourseUsefulLinkItem>? items)
    {
        var list = items ?? Array.Empty<CourseUsefulLinkItem>();
        if (list.Count == 0)
            return Array.Empty<CourseUsefulLinkItem>();

        return list
            .Select(i =>
            {
                var url = (i.Url ?? "").Trim();
                var title = (i.Title ?? "").Trim();
                if (title.Length == 0)
                    title = url;
                return new CourseUsefulLinkItem(title, url);
            })
            .ToList();
    }
}
