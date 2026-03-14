using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using CourseSync.Api.Data;
using Google.Apis.Auth.OAuth2;
using Microsoft.Extensions.Options;

namespace CourseSync.Api.Infrastructure.Push;

public sealed class FcmPushSender : IPushSender
{
    private const string FirebaseMessagingScope = "https://www.googleapis.com/auth/firebase.messaging";

    private readonly HttpClient _httpClient;
    private readonly FcmOptions _options;
    private readonly ILogger<FcmPushSender> _log;

    public FcmPushSender(HttpClient httpClient, IOptions<FcmOptions> options, ILogger<FcmPushSender> log)
    {
        _httpClient = httpClient;
        _options = options.Value;
        _log = log;
    }

    public async Task SendAsync(UserDevice device, Notification notification, CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(_options.ProjectId))
        {
            _log.LogDebug("FCM ProjectId not configured; skipping push to {UserId}", device.UserId);
            return;
        }

        GoogleCredential credential;
        if (!string.IsNullOrWhiteSpace(_options.CredentialsPath) && File.Exists(_options.CredentialsPath))
        {
            await using var stream = File.OpenRead(_options.CredentialsPath);
            credential = GoogleCredential.FromStream(stream).CreateScoped(FirebaseMessagingScope);
        }
        else
        {
            credential = GoogleCredential.GetApplicationDefault().CreateScoped(FirebaseMessagingScope);
        }

        var tokenAccess = credential as ITokenAccess ?? throw new InvalidOperationException("Credential does not support token access.");
        var accessToken = await tokenAccess.GetAccessTokenForRequestAsync(cancellationToken: ct);

        var url = $"https://fcm.googleapis.com/v1/projects/{_options.ProjectId}/messages:send";
        var payload = new
        {
            message = new
            {
                token = device.Token,
                notification = new
                {
                    title = notification.Title,
                    body = notification.Body
                },
                data = new Dictionary<string, string>
                {
                    ["type"] = notification.Type,
                    ["groupId"] = notification.GroupId.ToString(),
                    ["notificationId"] = notification.Id.ToString()
                }
            }
        };

        var json = JsonSerializer.Serialize(payload);
        using var req = new HttpRequestMessage(HttpMethod.Post, url)
        {
            Content = new StringContent(json, Encoding.UTF8, "application/json")
        };
        req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", accessToken);

        using var resp = await _httpClient.SendAsync(req, ct);
        if (!resp.IsSuccessStatusCode)
        {
            var body = await resp.Content.ReadAsStringAsync(ct);
            _log.LogWarning("FCM send failed: {Status} {Body}", resp.StatusCode, body);
            throw new InvalidOperationException($"FCM send failed: {resp.StatusCode}");
        }
    }
}
