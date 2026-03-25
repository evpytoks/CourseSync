namespace CourseSync.Api.Infrastructure;

public static class CourseMaterialUploadLimits
{
    public const long MaxPdfBytes = 100 * 1024 * 1024;
    public const long MaxMultipartRequestBytes = 105 * 1024 * 1024;
}
