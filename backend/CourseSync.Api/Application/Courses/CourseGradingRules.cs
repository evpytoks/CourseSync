namespace CourseSync.Api.Application.Courses;

public static class CourseGradingRules
{
    public const int GradingTextMaxLength = 3000;
    public const int GradingElementNameMaxLength = 50;
    public const int GradingElementCountMin = 1;
    public const int GradingElementCountMax = 100;
    public const decimal GradingScoreMin = 0m;
    public const decimal GradingScoreMax = 10m;

    public static (bool Valid, string? ErrorCode) ValidateGradingText(string? text)
    {
        if ((text ?? "").Length > GradingTextMaxLength)
            return (false, "grading_text_too_long");
        return (true, null);
    }

    public static (bool Valid, string? ErrorCode) ValidateGradingElements(IReadOnlyList<(string Name, decimal Coefficient)> elements)
    {
        if (elements.Count == 0)
            return (true, null);

        decimal sum = 0m;
        foreach (var element in elements)
        {
            var name = (element.Name ?? "").Trim();
            if (name.Length == 0)
                return (false, "grading_element_name_required");
            if (name.Length > GradingElementNameMaxLength)
                return (false, "grading_element_name_too_long");
            if (element.Coefficient < 0m || element.Coefficient > 1m)
                return (false, "grading_coefficient_out_of_range");
            sum += element.Coefficient;
        }

        if (Math.Abs(sum - 1m) > 0.0001m)
            return (false, "grading_coefficients_sum_must_equal_1");

        return (true, null);
    }
}
