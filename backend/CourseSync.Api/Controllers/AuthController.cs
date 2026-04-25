using CourseSync.Api.Models;
using CourseSync.Api.Infrastructure;
using CourseSync.Api.Infrastructure.Email;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Mvc;
using System.Net.Mail;
using Microsoft.Extensions.Options;

namespace CourseSync.Api.Controllers;

[ApiController]
[Route("auth")]
public sealed class AuthController : ControllerBase
{
    private const string TestEmailPrefix = "test";
    private const string TestLoginCode = "111111";

    private readonly IAuthLoginCodeService _codes;
    private readonly IJwtTokenService _jwt;
    private readonly IConfiguration _cfg;
    private readonly IEmailSender _email;
    private readonly ILogger<AuthController> _log;
    private readonly IUserService _userService;
    private readonly IRefreshTokenService _refresh;
    private readonly AuthCodeOptions _authOpt;


    public AuthController(
        IAuthLoginCodeService codes,
        IJwtTokenService jwt,
        IOptions<AuthCodeOptions> authOpt,
        IConfiguration cfg,
        IEmailSender email,
        ILogger<AuthController> log,
        IUserService userService,
        IRefreshTokenService refresh)
    {
        _codes = codes;
        _jwt = jwt;
        _authOpt = authOpt.Value;
        _cfg = cfg;
        _email = email;
        _log = log;
        _userService = userService;
        _refresh = refresh;
    }

    [HttpPost("send-code")]
    public async Task<ActionResult<SendCodeResponse>> SendCode([FromBody] SendCodeRequest req, CancellationToken ct)
    {
        if (req is null)
            return ErrorResponse("email_required");

        var email = (req.Email ?? "").Trim();

        var emailValidation = ValidateAllowedEmail(email);
        if (emailValidation is not null) return BadRequest(emailValidation);

        var user = await _userService.GetOrCreateByEmailAsync(email, ct);

        var ttl = _authOpt.CodeTtlSeconds;
        var isTestEmail = IsTestEmail(email);
        var codeOverride = isTestEmail ? TestLoginCode : null;
        var (status, requestId, expiresAt, code) = await _codes.CreateAsync(user, ttl, _authOpt.SendCooldownSeconds, codeOverride, ct);

        if (status == CreateAuthCodeStatus.RateLimited)
            return ErrorResponse("rate_limited", 429);

        if (!isTestEmail)
        {
            try
            {
                await _email.SendAuthCodeAsync(user.Email, code, ttl, ct);
            }
            catch (Exception ex)
            {
                await _codes.InvalidateAsync(requestId, ct);

                _log.LogError(ex,
                    "Failed to send auth email. smtpEnabled={Enabled} host={Host} port={Port} security={Security} from={From} to={To}",
                    _cfg.GetValue<bool>("Smtp:Enabled"),
                    _cfg["Smtp:Host"],
                    _cfg["Smtp:Port"],
                    _cfg["Smtp:Security"],
                    _cfg["Smtp:FromEmail"],
                    email);

                return ErrorResponse("email_service_unavailable", 503);
            }
        }

        await _codes.MarkCodeSentAsync(user.Id, ct);
        return Ok(new SendCodeResponse(requestId, expiresAt));
    }

    [HttpPost("login")]
    public async Task<ActionResult<LoginResponse>> Login([FromBody] LoginRequest req, CancellationToken ct)
    {
        if (req is null)
            return ErrorResponse("email_required");

        var email = (req.Email ?? "").Trim();
        var requestId = (req.RequestId ?? "").Trim();
        var code = (req.Code ?? "").Trim();

        var emailValidation = ValidateAllowedEmail(email);
        if (emailValidation is not null) return BadRequest(emailValidation);

        if (string.IsNullOrWhiteSpace(requestId))
            return ErrorResponse("request_id_required");

        if (string.IsNullOrWhiteSpace(code))
            return ErrorResponse("code_required");

        var maxAttempts = _authOpt.MaxAttempts;

        var result = await _codes.VerifyAsync(email, requestId, code, maxAttempts, ct);

        if (result == VerifyResult.TooManyAttempts)
            return ErrorResponse("code_attempts_exceeded", 429);

        if (result == VerifyResult.Invalid)
            return ErrorResponse("invalid_code", 401);

        var user = await _userService.FindByEmailAsync(email, ct);
        if (user is null)
            return ErrorResponse("invalid_code", 401);

        var userId = user.Id;
        await _userService.ClearCurrentGroupAsync(userId, ct);

        var (refreshToken, _, tokenVersion) = await _refresh.EstablishSingleSessionAsync(userId, ct);
        var token = _jwt.CreateToken(userId, user.Email, tokenVersion);

        return Ok(new LoginResponse(token, refreshToken));
    }

    [HttpPost("refresh")]
    public async Task<ActionResult<RefreshResponse>> Refresh([FromBody] RefreshRequest req, CancellationToken ct)
    {
        if (req is null)
            return ErrorResponse("refresh_token_required");

        var refreshToken = (req.RefreshToken ?? "").Trim();
        if (string.IsNullOrWhiteSpace(refreshToken))
            return ErrorResponse("refresh_token_required");

        var (status, userId, newRefreshToken, _) = await _refresh.RotateAsync(refreshToken, ct);
        if (status == RefreshRotateStatus.Reused)
            return ErrorResponse("refresh_reused", 401);

        if (status != RefreshRotateStatus.Ok)
            return ErrorResponse("invalid_refresh_token", 401);

        var user = await _userService.FindByIdAsync(userId, ct);
        if (user is null)
            return ErrorResponse("invalid_refresh_token", 401);

        var token = _jwt.CreateToken(userId, user.Email, user.TokenVersion);
        return Ok(new RefreshResponse(token, newRefreshToken));
    }

    [HttpPost("logout")]
    public async Task<IActionResult> Logout([FromBody] RefreshRequest req, CancellationToken ct)
    {
        if (req is null)
            return ErrorResponse("refresh_token_required");

        var refreshToken = (req.RefreshToken ?? "").Trim();
        if (string.IsNullOrWhiteSpace(refreshToken))
            return ErrorResponse("refresh_token_required");

        await _refresh.RevokeAsync(refreshToken, ct);
        return NoContent();
    }

    private ErrorEnvelope? ValidateAllowedEmail(string email)
    {
        email = (email ?? "").Trim();

        if (string.IsNullOrWhiteSpace(email))
            return new ErrorEnvelope(new ApiError("email_required"));

        if (!MailAddress.TryCreate(email, out var addr) || !string.Equals(addr.Address, email, StringComparison.OrdinalIgnoreCase))
            return new ErrorEnvelope(new ApiError("invalid_email"));

        if (!string.Equals(addr.Host, _authOpt.AllowedEmailDomain, StringComparison.OrdinalIgnoreCase))
            return new ErrorEnvelope(new ApiError("not_hse_email"));

        return null;
    }

    private ActionResult ErrorResponse(string code, int statusCode = 400)
        => StatusCode(statusCode, new ErrorEnvelope(new ApiError(code)));

    private static bool IsTestEmail(string email)
    {
        var normalized = (email ?? "").Trim();
        return normalized.StartsWith(TestEmailPrefix, StringComparison.OrdinalIgnoreCase);
    }
}
