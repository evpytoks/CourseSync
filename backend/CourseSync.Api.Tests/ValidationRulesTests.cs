using System.Linq;
using CourseSync.Api.Application.Courses;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class ValidationRulesTests
{
    [Fact]
    public void Course_general_info_allows_null_and_empty_and_enforces_max_length()
    {
        Assert.True(CourseInputRules.ValidateGeneralInfo(null).Valid);
        Assert.True(CourseInputRules.ValidateGeneralInfo("").Valid);

        var ok2000 = CourseInputRules.ValidateGeneralInfo(new string('x', 2000));
        Assert.True(ok2000.Valid);

        var tooLong = CourseInputRules.ValidateGeneralInfo(new string('x', 2001));
        Assert.False(tooLong.Valid);
        Assert.Equal("general_info_too_long", tooLong.ErrorCode);
    }

    [Fact]
    public void Course_contacts_validates_people_and_contact_methods()
    {
        Assert.False(CourseInputRules.ValidateContacts(null).Valid);
        Assert.Equal("contacts_required", CourseInputRules.ValidateContacts(null).ErrorCode);
        Assert.True(CourseInputRules.ValidateContacts(Array.Empty<CourseContactPersonItem>()).Valid);

        var valid = CourseInputRules.ValidateContacts(new[]
        {
            new CourseContactPersonItem(
                "Иванов Иван Иванович",
                new[]
                {
                    new CourseContactMethodItem("Почта", "teacher@example.com"),
                    new CourseContactMethodItem("", "+70000000000")
                })
        });
        Assert.True(valid.Valid);

        var noName = CourseInputRules.ValidateContacts(new[]
        {
            new CourseContactPersonItem("   ", Array.Empty<CourseContactMethodItem>())
        });
        Assert.False(noName.Valid);
        Assert.Equal("contact_person_name_required", noName.ErrorCode);

        var methodsRequired = CourseInputRules.ValidateContacts(new[]
        {
            new CourseContactPersonItem("Иван Иванов", null)
        });
        Assert.False(methodsRequired.Valid);
        Assert.Equal("contact_methods_required", methodsRequired.ErrorCode);

        var tooManyPeople = Enumerable.Range(0, CourseInputRules.ContactsMaxPeople + 1)
            .Select(i => new CourseContactPersonItem($"P{i}", Array.Empty<CourseContactMethodItem>()))
            .ToArray();
        Assert.False(CourseInputRules.ValidateContacts(tooManyPeople).Valid);
        Assert.Equal("contacts_too_many_people", CourseInputRules.ValidateContacts(tooManyPeople).ErrorCode);

        var tooManyMethods = CourseInputRules.ValidateContacts(new[]
        {
            new CourseContactPersonItem(
                "P1",
                Enumerable.Range(0, CourseInputRules.ContactMethodsMaxPerPerson + 1)
                    .Select(i => new CourseContactMethodItem("Телеграм", "значение_" + i))
                    .ToArray())
        });
        Assert.False(tooManyMethods.Valid);
        Assert.Equal("contact_methods_too_many", tooManyMethods.ErrorCode);

        var emptyValue = CourseInputRules.ValidateContacts(new[]
        {
            new CourseContactPersonItem(
                "P1",
                new[] { new CourseContactMethodItem("Телефон", "  ") })
        });
        Assert.False(emptyValue.Valid);
        Assert.Equal("contact_method_value_required", emptyValue.ErrorCode);

        var typeRequired = CourseInputRules.ValidateContacts(new[]
        {
            new CourseContactPersonItem(
                "P1",
                new[] { new CourseContactMethodItem(null, "+70000000000") })
        });
        Assert.False(typeRequired.Valid);
        Assert.Equal("contact_method_type_required", typeRequired.ErrorCode);
    }

    [Fact]
    public void Course_useful_links_validates_list_and_lengths()
    {
        Assert.True(CourseInputRules.ValidateUsefulLinks(null).Valid);
        Assert.True(CourseInputRules.ValidateUsefulLinks(Array.Empty<CourseUsefulLinkItem>()).Valid);

        Assert.True(CourseInputRules.ValidateUsefulLinks(new[] { new CourseUsefulLinkItem("", "https://a.com") }).Valid);
        var normalized = CourseInputRules.NormalizeUsefulLinks(new[] { new CourseUsefulLinkItem("", "https://a.com") });
        Assert.Single(normalized);
        Assert.Equal("https://a.com", normalized[0].Title);
        Assert.Equal("https://a.com", normalized[0].Url);

        Assert.False(CourseInputRules.ValidateUsefulLinks(new[] { new CourseUsefulLinkItem("Сайт курса", "") }).Valid);
        Assert.Equal("useful_link_url_invalid", CourseInputRules.ValidateUsefulLinks(new[] { new CourseUsefulLinkItem("Сайт курса", "") }).ErrorCode);

        Assert.False(CourseInputRules.ValidateUsefulLinks(new[] { new CourseUsefulLinkItem(new string('t', 51), "https://a.com") }).Valid);
        Assert.Equal("useful_link_title_invalid", CourseInputRules.ValidateUsefulLinks(new[] { new CourseUsefulLinkItem(new string('t', 51), "https://a.com") }).ErrorCode);

        Assert.False(CourseInputRules.ValidateUsefulLinks(new[] { new CourseUsefulLinkItem("Сайт курса", new string('u', 201)) }).Valid);
        Assert.Equal("useful_link_url_invalid", CourseInputRules.ValidateUsefulLinks(new[] { new CourseUsefulLinkItem("Сайт курса", new string('u', 201)) }).ErrorCode);

        var many = Enumerable.Range(0, CourseInputRules.UsefulLinksMaxItems + 1)
            .Select(i => new CourseUsefulLinkItem("Ссылка " + i, "https://example.com/" + i))
            .ToArray();
        Assert.False(CourseInputRules.ValidateUsefulLinks(many).Valid);
        Assert.Equal("useful_links_too_many", CourseInputRules.ValidateUsefulLinks(many).ErrorCode);
    }

    [Fact]
    public void News_text_is_required_and_has_3000_max()
    {
        var empty = NewsService.ValidateNewsText(null);
        Assert.False(empty.Valid);
        Assert.Equal("news_text_required", empty.ErrorCode);

        var ok = NewsService.ValidateNewsText(new string('t', 3000));
        Assert.True(ok.Valid);

        var tooLong = NewsService.ValidateNewsText(new string('t', 3001));
        Assert.False(tooLong.Valid);
        Assert.Equal("news_text_too_long", tooLong.ErrorCode);
    }

    [Fact]
    public void Calendar_limits_are_name_50_and_description_1000()
    {
        var name50 = CalendarService.ValidateEventName(new string('n', 50));
        Assert.True(name50.Valid);

        var name51 = CalendarService.ValidateEventName(new string('n', 51));
        Assert.False(name51.Valid);
        Assert.Equal("calendar_name_too_long", name51.ErrorCode);

        var desc1000 = CalendarService.ValidateDescription(new string('d', 1000));
        Assert.True(desc1000.Valid);

        var desc1001 = CalendarService.ValidateDescription(new string('d', 1001));
        Assert.False(desc1001.Valid);
        Assert.Equal("calendar_description_too_long", desc1001.ErrorCode);
    }
}
