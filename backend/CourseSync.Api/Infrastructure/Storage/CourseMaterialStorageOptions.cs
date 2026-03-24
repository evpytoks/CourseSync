namespace CourseSync.Api.Infrastructure.Storage;

public enum CourseMaterialStorageKind
{
    Local = 0,
    S3 = 1
}

public sealed class CourseMaterialS3Options
{
    public string ServiceUrl { get; set; } = "";

    public string BucketName { get; set; } = "";
    public string AccessKey { get; set; } = "";
    public string SecretKey { get; set; } = "";

    public bool ForcePathStyle { get; set; } = true;

    public string? Region { get; set; }
}

public sealed class CourseMaterialStorageOptions
{
    public const string SectionName = "CourseMaterials";

    public CourseMaterialStorageKind Kind { get; set; } = CourseMaterialStorageKind.Local;

    public CourseMaterialS3Options? S3 { get; set; }

    public static void Validate(CourseMaterialStorageOptions o)
    {
        if (o.Kind != CourseMaterialStorageKind.S3)
            return;

        if (o.S3 is null)
            throw new InvalidOperationException("CourseMaterials:S3 is required when Kind is S3.");

        if (string.IsNullOrWhiteSpace(o.S3.ServiceUrl))
            throw new InvalidOperationException("CourseMaterials:S3:ServiceUrl is required.");

        if (string.IsNullOrWhiteSpace(o.S3.BucketName))
            throw new InvalidOperationException("CourseMaterials:S3:BucketName is required.");

        if (string.IsNullOrWhiteSpace(o.S3.AccessKey))
            throw new InvalidOperationException("CourseMaterials:S3:AccessKey is required.");

        if (string.IsNullOrWhiteSpace(o.S3.SecretKey))
            throw new InvalidOperationException("CourseMaterials:S3:SecretKey is required.");
    }
}
