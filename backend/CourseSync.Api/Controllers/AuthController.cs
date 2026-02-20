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
    private readonly AuthLoginCodeService _codes;
    private readonly JwtTokenService _jwt;
    private readonly IConfiguration _cfg;
    private readonly IEmailSender _email;
    private readonly ILogger<AuthController> _log;
    private readonly UserService _userService;
    private readonly RefreshTokenService _refresh;
    private readonly AuthCodeOptions _authOpt;


    public AuthController(AuthLoginCodeService codes, JwtTokenService jwt, IOptions<AuthCodeOptions> authOpt, IConfiguration cfg, IEmailSender email, ILogger<AuthController> log, UserService userService, RefreshTokenService refresh)
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

        if (!IsValidEmail(email))
            return BadRequest(new ErrorEnvelope(new ApiError("invalid_email", "Некорректная почта")));

        var user = await _userService.FindByEmailAsync(email, ct);
        if (user is null)
            return BadRequest(new ErrorEnvelope(new ApiError("invalid_email", "Некорректная почта")));

        var ttl = _authOpt.CodeTtlSeconds;
        var (requestId, expiresInSec, code) = await _codes.CreateAsync(user, ttl, ct);

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

            return StatusCode(500, new ErrorEnvelope(new ApiError("email_send_failed", "Ошибка при отправке кода на почту. Попробуйте позже")));
        }

        return Ok(new SendCodeResponse(requestId, expiresInSec));
    }

    [HttpPost("login")]
    public async Task<ActionResult<LoginResponse>> Login([FromBody] LoginRequest req, CancellationToken ct)
    {
        var email = (req.Email ?? "").Trim();
        var requestId = (req.RequestId ?? "").Trim();
        var code = (req.Code ?? "").Trim();

        if (!IsValidEmail(email))
            return BadRequest(new ErrorEnvelope(new ApiError("invalid_email", "Некорректная почта")));

        if (string.IsNullOrWhiteSpace(requestId) || string.IsNullOrWhiteSpace(code))
            return BadRequest(new ErrorEnvelope(new ApiError("validation_error", "requestId and code are required")));

        var user = await _userService.FindByEmailAsync(email, ct);
        if (user is null)
            return BadRequest(new ErrorEnvelope(new ApiError("invalid_email", "Некорректная почта")));

        var maxAttempts = _authOpt.MaxAttempts;

        var result = await _codes.VerifyAsync(email, requestId, code, maxAttempts, ct);

        if (result == VerifyResult.TooManyAttempts)
            return StatusCode(429, new ErrorEnvelope(new ApiError("too_many_attempts", "Слишком много попыток. Попробуйте позже")));

        if (result == VerifyResult.Invalid)
            return Unauthorized(new ErrorEnvelope(new ApiError("invalid_code", "Неверный или просроченный код")));

        var userId = user.Id;

        var tokenVersion = await _userService.BumpTokenVersionAsync(userId, ct);
        var token = _jwt.CreateToken(userId, user.Email, tokenVersion);
        var (refreshToken, _) = await _refresh.IssueSingleSessionAsync(userId, ct);

        return Ok(new LoginResponse(token, refreshToken, new UserDto(userId, user.Email)));
    }

    [HttpPost("refresh")]
    public async Task<ActionResult<RefreshResponse>> Refresh([FromBody] RefreshRequest req, CancellationToken ct)
    {
        var refreshToken = (req.RefreshToken ?? "").Trim();
        if (string.IsNullOrWhiteSpace(refreshToken))
            return BadRequest(new ErrorEnvelope(new ApiError("validation_error", "refreshToken is required")));

        var (status, userId, newRefreshToken, _) = await _refresh.RotateAsync(refreshToken, ct);
        if (status == RefreshRotateStatus.Reused)
            return Unauthorized(new ErrorEnvelope(new ApiError("refresh_reused", "Сессия недействительна. Войдите заново")));

        if (status != RefreshRotateStatus.Ok)
            return Unauthorized(new ErrorEnvelope(new ApiError("invalid_refresh_token", "Недействительный refresh token")));

        var user = await _userService.FindByIdAsync(userId, ct);
        if (user is null)
            return Unauthorized(new ErrorEnvelope(new ApiError("invalid_refresh_token", "Недействительный refresh token")));

        var token = _jwt.CreateToken(userId, user.Email, user.TokenVersion);
        return Ok(new RefreshResponse(token, newRefreshToken));
    }

    private static bool IsValidEmail(string email)
        => MailAddress.TryCreate((email ?? "").Trim(), out var addr)
           && string.Equals(addr.Address, (email ?? "").Trim(), StringComparison.OrdinalIgnoreCase);
}
