using CourseSync.Api.Models;
using CourseSync.Api.Infrastructure.Email;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Mvc;

namespace CourseSync.Api.Controllers;

[ApiController]
[Route("auth")]
public sealed class AuthController : ControllerBase
{
    private readonly AuthCodeStore _codes;
    private readonly JwtTokenService _jwt;
    private readonly IConfiguration _cfg;
    private readonly IEmailSender _email;
    private readonly ILogger<AuthController> _log;

    public AuthController(AuthCodeStore codes, JwtTokenService jwt, IConfiguration cfg, IEmailSender email, ILogger<AuthController> log)
{
    _codes = codes;
    _jwt = jwt;
    _cfg = cfg;
    _email = email;
    _log = log;
}

    [HttpPost("send-code")]
    public async Task<ActionResult<SendCodeResponse>> SendCode([FromBody] SendCodeRequest req, CancellationToken ct)
    {
        var email = (req.Email ?? "").Trim();

        if (string.IsNullOrWhiteSpace(email) || !email.Contains('@'))
            return BadRequest(new ErrorEnvelope(new ApiError("validation_error", "Email is invalid")));

        var ttl = _cfg.GetValue("AuthCode:CodeTtlSeconds", 300);
        var (requestId, expiresInSec, code) = _codes.CreateCode(email, ttl);

        try
        {
            await _email.SendAuthCodeAsync(email, code, ttl, ct);
        }
        catch (Exception ex)
        {
            _log.LogError(ex,
                "Failed to send auth email. smtpEnabled={Enabled} host={Host} port={Port} security={Security} from={From} to={To}",
                _cfg.GetValue<bool>("Smtp:Enabled"),
                _cfg["Smtp:Host"],
                _cfg["Smtp:Port"],
                _cfg["Smtp:Security"],
                _cfg["Smtp:FromEmail"],
                email);

            return StatusCode(500, new ErrorEnvelope(new ApiError("email_send_failed", "Failed to send email")));
        }

        return Ok(new SendCodeResponse(requestId, expiresInSec));
    }

    [HttpPost("login")]
    public ActionResult<LoginResponse> Login([FromBody] LoginRequest req)
    {
        var email = (req.Email ?? "").Trim();
        var requestId = (req.RequestId ?? "").Trim();
        var code = (req.Code ?? "").Trim();

        if (string.IsNullOrWhiteSpace(email) ||
            string.IsNullOrWhiteSpace(requestId) ||
            string.IsNullOrWhiteSpace(code))
        {
            return BadRequest(new ErrorEnvelope(new ApiError("validation_error", "Email, requestId and code are required")));
        }

        var maxAttempts = _cfg.GetValue("AuthCode:MaxAttempts", 5);

        var result = _codes.VerifyCode(email, requestId, code, maxAttempts);

        if (result == VerifyResult.TooManyAttempts)
            return StatusCode(429, new ErrorEnvelope(new ApiError("too_many_attempts", "Try again later")));

        if (result == VerifyResult.Invalid)
            return Unauthorized(new ErrorEnvelope(new ApiError("invalid_code", "Code is invalid or expired")));

        // MVP: пользователь в памяти — id стабильно вычисляем из email
        var userId = StableUserId(email);

        var token = _jwt.CreateToken(userId, email);

        return Ok(new LoginResponse(token, new UserDto(userId, email)));
    }

    private static int StableUserId(string email)
    {
        unchecked
        {
            int hash = 23;
            foreach (var ch in email.ToLowerInvariant())
                hash = hash * 31 + ch;
            return Math.Abs(hash);
        }
    }
}
