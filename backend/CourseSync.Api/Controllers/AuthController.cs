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
    private const string AllowedEmailDomain = "edu.hse.ru";

    private readonly AuthLoginCodeService _codes;
    private readonly JwtTokenService _jwt;
    private readonly IConfiguration _cfg;
    private readonly IEmailSender _email;
    private readonly ILogger<AuthController> _log;
    private readonly UserService _userService;
    private readonly RefreshTokenService _refresh;
    private readonly AuthCodeOptions _authOpt;


    public AuthController(
        AuthLoginCodeService codes,
        JwtTokenService jwt,
        IOptions<AuthCodeOptions> authOpt,
        IConfiguration cfg,
        IEmailSender email,
        ILogger<AuthController> log,
        UserService userService,
        RefreshTokenService refresh)
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
        var email = (req.Email ?? "").Trim();

        var emailValidation = ValidateAllowedEmail(email);
        if (emailValidation is not null) return BadRequest(emailValidation);

        var user = await _userService.GetOrCreateByEmailAsync(email, ct);

        var ttl = _authOpt.CodeTtlSeconds;
        var (status, requestId, expiresAt, code) = await _codes.CreateAsync(user, ttl, _authOpt.SendCooldownSeconds, ct);

        if (status == CreateAuthCodeStatus.RateLimited)
            return StatusCode(429, new ErrorEnvelope(new ApiError("rate_limited")));

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

            return StatusCode(500, new ErrorEnvelope(new ApiError("email_send_failed")));
        }

        await _codes.MarkCodeSentAsync(user.Id, ct);
        return Ok(new SendCodeResponse(requestId, expiresAt));
    }

    [HttpPost("login")]
    public async Task<ActionResult<LoginResponse>> Login([FromBody] LoginRequest req, CancellationToken ct)
    {
        var email = (req.Email ?? "").Trim();
        var requestId = (req.RequestId ?? "").Trim();
        var code = (req.Code ?? "").Trim();

        var emailValidation = ValidateAllowedEmail(email);
        if (emailValidation is not null) return BadRequest(emailValidation);

        if (string.IsNullOrWhiteSpace(requestId))
            return BadRequest(new ErrorEnvelope(new ApiError("request_id_required")));

        if (string.IsNullOrWhiteSpace(code))
            return BadRequest(new ErrorEnvelope(new ApiError("code_required")));

        var maxAttempts = _authOpt.MaxAttempts;

        var result = await _codes.VerifyAsync(email, requestId, code, maxAttempts, ct);

        if (result == VerifyResult.TooManyAttempts)
            return StatusCode(429, new ErrorEnvelope(new ApiError("code_attempts_exceeded")));

        if (result == VerifyResult.Invalid)
            return Unauthorized(new ErrorEnvelope(new ApiError("invalid_code")));

        var user = await _userService.FindByEmailAsync(email, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("invalid_code")));

        var userId = user.Id;
        await _userService.ClearCurrentGroupAsync(userId, ct);

        var (refreshToken, _, tokenVersion) = await _refresh.EstablishSingleSessionAsync(userId, ct);
        var token = _jwt.CreateToken(userId, user.Email, tokenVersion);

        return Ok(new LoginResponse(token, refreshToken));
    }

    [HttpPost("refresh")]
    public async Task<ActionResult<RefreshResponse>> Refresh([FromBody] RefreshRequest req, CancellationToken ct)
    {
        var refreshToken = (req.RefreshToken ?? "").Trim();
        if (string.IsNullOrWhiteSpace(refreshToken))
            return BadRequest(new ErrorEnvelope(new ApiError("refresh_token_required")));

        var (status, userId, newRefreshToken, _) = await _refresh.RotateAsync(refreshToken, ct);
        if (status == RefreshRotateStatus.Reused)
            return Unauthorized(new ErrorEnvelope(new ApiError("refresh_reused")));

        if (status != RefreshRotateStatus.Ok)
            return Unauthorized(new ErrorEnvelope(new ApiError("invalid_refresh_token")));

        var user = await _userService.FindByIdAsync(userId, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("invalid_refresh_token")));

        var token = _jwt.CreateToken(userId, user.Email, user.TokenVersion);
        return Ok(new RefreshResponse(token, newRefreshToken));
    }

    [HttpPost("logout")]
    public async Task<IActionResult> Logout([FromBody] RefreshRequest req, CancellationToken ct)
    {
        var refreshToken = (req.RefreshToken ?? "").Trim();
        if (string.IsNullOrWhiteSpace(refreshToken))
            return BadRequest(new ErrorEnvelope(new ApiError("refresh_token_required")));

        await _refresh.RevokeAsync(refreshToken, ct);
        return NoContent();
    }

    private static ErrorEnvelope? ValidateAllowedEmail(string email)
    {
        email = (email ?? "").Trim();

        if (string.IsNullOrWhiteSpace(email))
            return new ErrorEnvelope(new ApiError("email_required"));

        if (!MailAddress.TryCreate(email, out var addr) || !string.Equals(addr.Address, email, StringComparison.OrdinalIgnoreCase))
            return new ErrorEnvelope(new ApiError("invalid_email"));

        if (!string.Equals(addr.Host, AllowedEmailDomain, StringComparison.OrdinalIgnoreCase))
            return new ErrorEnvelope(new ApiError("not_hse_email"));

        return null;
    }
}
