namespace CourseSync.Api.Infrastructure.Push;

public sealed class FcmOptions
{
    public const string SectionName = "Fcm";

    public string ProjectId { get; set; } = "";

    public string? CredentialsPath { get; set; }
}
