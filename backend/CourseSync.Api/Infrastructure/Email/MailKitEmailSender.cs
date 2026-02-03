using MailKit.Net.Smtp;
using MailKit.Security;
using MimeKit;
using Microsoft.Extensions.Options;

namespace CourseSync.Api.Infrastructure.Email;

public sealed class MailKitEmailSender : IEmailSender
{
    private readonly SmtpOptions _opt;
    private readonly ILogger<MailKitEmailSender> _log;

    public MailKitEmailSender(IOptions<SmtpOptions> opt, ILogger<MailKitEmailSender> log)
    {
        _opt = opt.Value;
        _opt.Validate();
        _log = log;
    }

    public async Task SendAuthCodeAsync(string toEmail, string code, int ttlSeconds, CancellationToken ct = default)
    {
        if (!_opt.Enabled)
            throw new InvalidOperationException("SMTP is disabled");

        var message = BuildAuthCodeMessage(toEmail, code, ttlSeconds);

        try
        {
            using var client = new SmtpClient();
            client.Timeout = _opt.TimeoutMs;

            var socket = MapSecurity(_opt.Security);

            _log.LogInformation("SMTP connect: {Host}:{Port} security={Security}", _opt.Host, _opt.Port, _opt.Security);

            await client.ConnectAsync(_opt.Host, _opt.Port, socket, ct);

            client.AuthenticationMechanisms.Remove("XOAUTH2");

            if (!string.IsNullOrWhiteSpace(_opt.Username))
            {
                _log.LogInformation("SMTP authenticate as {Username}", _opt.Username);
                await client.AuthenticateAsync(_opt.Username, _opt.Password, ct);
            }

            _log.LogInformation("SMTP send to {To}", toEmail);
            await client.SendAsync(message, ct);

            await client.DisconnectAsync(true, ct);
        }
        catch (Exception ex)
        {
            _log.LogError(ex,
                "SMTP send failed (host={Host}, port={Port}, security={Security}, from={From}, to={To})",
                _opt.Host, _opt.Port, _opt.Security, _opt.FromEmail, toEmail);
            throw;
        }
    }

    private MimeMessage BuildAuthCodeMessage(string toEmail, string code, int ttlSeconds)
    {
        var msg = new MimeMessage();
        msg.From.Add(new MailboxAddress(_opt.FromName, _opt.FromEmail));
        msg.To.Add(MailboxAddress.Parse(toEmail));
        msg.Subject = "CourseSync: код для входа";

        var minutes = Math.Max(1, ttlSeconds / 60);

        var plain =
$@"Ваш код для входа в CourseSync: {code}

Срок действия: {minutes} мин.

Если это были не вы — просто проигнорируйте письмо.";

        var html =
$@"<p>Ваш код для входа в <b>CourseSync</b>:</p>
<p style=""font-size:24px;letter-spacing:2px""><b>{code}</b></p>
<p>Срок действия: <b>{minutes}</b> мин.</p>
<p style=""color:#666"">Если это были не вы — просто проигнорируйте письмо.</p>";

        var body = new BodyBuilder
        {
            TextBody = plain,
            HtmlBody = html
        };

        msg.Body = body.ToMessageBody();
        return msg;
    }

    private static SecureSocketOptions MapSecurity(string security) =>
        (security ?? "").Trim().ToLowerInvariant() switch
        {
            "auto" => SecureSocketOptions.Auto,
            "starttls" => SecureSocketOptions.StartTls,
            "starttlswhenavailable" => SecureSocketOptions.StartTlsWhenAvailable,
            "sslonconnect" => SecureSocketOptions.SslOnConnect,
            "none" => SecureSocketOptions.None,
            _ => SecureSocketOptions.StartTls
        };
}
