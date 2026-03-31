using CourseSync.Api.Services;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class ValidationRulesTests
{
    [Fact]
    public void Course_general_info_is_required_but_allows_empty_string()
    {
        var nullResult = CourseService.ValidateGeneralInfo(null);
        Assert.False(nullResult.Valid);
        Assert.Equal("general_info_required", nullResult.ErrorCode);

        var emptyResult = CourseService.ValidateGeneralInfo("");
        Assert.True(emptyResult.Valid);
        Assert.Null(emptyResult.ErrorCode);
    }

    [Fact]
    public void Course_useful_links_is_required_and_has_1000_max()
    {
        var nullResult = CourseService.ValidateUsefulLinks(null);
        Assert.False(nullResult.Valid);
        Assert.Equal("useful_links_required", nullResult.ErrorCode);

        var emptyResult = CourseService.ValidateUsefulLinks("");
        Assert.True(emptyResult.Valid);

        var maxResult = CourseService.ValidateUsefulLinks(new string('x', 1000));
        Assert.True(maxResult.Valid);

        var overResult = CourseService.ValidateUsefulLinks(new string('x', 1001));
        Assert.False(overResult.Valid);
        Assert.Equal("useful_links_too_long", overResult.ErrorCode);
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
