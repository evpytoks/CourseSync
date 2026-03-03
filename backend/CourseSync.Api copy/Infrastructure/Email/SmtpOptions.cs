namespace CourseSync.Api.Infrastructure.Email;

public sealed class SmtpOptions
{
    public const string SectionName = "Smtp";

    public bool Enabled { get; set; } = false;

    public string Host { get; set; } = "";
    public int Port { get; set; } = 587;

    public string Security { get; set; } = "StartTls";

    public string Username { get; set; } = "";
    public string Password { get; set; } = "";

    public string FromEmail { get; set; } = "noreply@coursesync.local";
    public string FromName { get; set; } = "CourseSync";

    public int TimeoutMs { get; set; } = 10000;

    public void Validate()
    {
        if (!Enabled) return;

        if (string.IsNullOrWhiteSpace(Host))
            throw new InvalidOperationException("Smtp:Host is missing");

        if (Port <= 0)
            throw new InvalidOperationException("Smtp:Port is invalid");

        if (string.IsNullOrWhiteSpace(FromEmail))
            throw new InvalidOperationException("Smtp:FromEmail is missing");
    }
}
