namespace CourseSync.Api.Infrastructure.Email;

public interface IEmailSender
{
    Task SendAuthCodeAsync(string toEmail, string code, int ttlSeconds, CancellationToken ct = default);
}
