using System.Linq;
using CourseSync.Api.Models;
using CourseSync.Api.Services;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class ValidationRulesTests
{
    [Fact]
    public void Course_general_info_allows_null_and_empty_and_enforces_max_length()
    {
        Assert.True(CourseService.ValidateGeneralInfo(null).Valid);
        Assert.True(CourseService.ValidateGeneralInfo("").Valid);

        var ok2000 = CourseService.ValidateGeneralInfo(new string('x', 2000));
        Assert.True(ok2000.Valid);

        var tooLong = CourseService.ValidateGeneralInfo(new string('x', 2001));
        Assert.False(tooLong.Valid);
        Assert.Equal("general_info_too_long", tooLong.ErrorCode);
    }

    [Fact]
    public void Course_useful_links_validates_list_and_lengths()
    {
        Assert.True(CourseService.ValidateUsefulLinks(null).Valid);
        Assert.True(CourseService.ValidateUsefulLinks(Array.Empty<CourseUsefulLinkItem>()).Valid);

        Assert.False(CourseService.ValidateUsefulLinks(new[] { new CourseUsefulLinkItem("", "https://a.com") }).Valid);
        Assert.Equal("useful_link_title_invalid", CourseService.ValidateUsefulLinks(new[] { new CourseUsefulLinkItem("", "https://a.com") }).ErrorCode);

        Assert.False(CourseService.ValidateUsefulLinks(new[] { new CourseUsefulLinkItem("A", "") }).Valid);
        Assert.Equal("useful_link_url_invalid", CourseService.ValidateUsefulLinks(new[] { new CourseUsefulLinkItem("A", "") }).ErrorCode);

        Assert.False(CourseService.ValidateUsefulLinks(new[] { new CourseUsefulLinkItem(new string('t', 51), "https://a.com") }).Valid);
        Assert.Equal("useful_link_title_invalid", CourseService.ValidateUsefulLinks(new[] { new CourseUsefulLinkItem(new string('t', 51), "https://a.com") }).ErrorCode);

        Assert.False(CourseService.ValidateUsefulLinks(new[] { new CourseUsefulLinkItem("A", new string('u', 201)) }).Valid);
        Assert.Equal("useful_link_url_invalid", CourseService.ValidateUsefulLinks(new[] { new CourseUsefulLinkItem("A", new string('u', 201)) }).ErrorCode);

        var many = Enumerable.Range(0, CourseService.UsefulLinksMaxItems + 1)
            .Select(i => new CourseUsefulLinkItem("L" + i, "https://x.co/" + i))
            .ToArray();
        Assert.False(CourseService.ValidateUsefulLinks(many).Valid);
        Assert.Equal("useful_links_too_many", CourseService.ValidateUsefulLinks(many).ErrorCode);
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
