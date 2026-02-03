namespace CourseSync.Api.Infrastructure.Email;

public sealed class ConsoleEmailSender : IEmailSender
{
    public Task SendAuthCodeAsync(string toEmail, string code, int ttlSeconds, CancellationToken ct = default)
    {
        Console.WriteLine($"[AUTH][DEV] Code for {toEmail}: {code} (ttl={ttlSeconds}s)");
        return Task.CompletedTask;
    }
}
