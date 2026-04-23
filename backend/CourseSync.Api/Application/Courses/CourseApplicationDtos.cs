using CourseSync.Api.Models;

namespace CourseSync.Api.Application.Courses;

public sealed record CourseListDto(Guid Id, string Name);

public sealed record CourseDetailDto(
    Guid Id,
    string Name,
    string GeneralInfo,
    IReadOnlyList<CourseContactPersonItem> Contacts,
    IReadOnlyList<CourseUsefulLinkItem> UsefulLinks);

public sealed record GradingElementDto(
    string Name,
    decimal Coefficient,
    decimal Block,
    int Count,
    decimal AverageScore,
    bool IsBlocked);

public sealed record GradingElementScoresRow(string Name, int Count, IReadOnlyList<decimal> Scores);

public sealed record CourseCumulativeGradeDetailDto(
    decimal Value,
    decimal Block,
    decimal? Automatic,
    bool IsBlocked,
    bool? IsAuto,
    IReadOnlyList<string> ElementNames);
