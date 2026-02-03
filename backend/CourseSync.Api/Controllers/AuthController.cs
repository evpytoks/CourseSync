using CourseSync.Api.Models;
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

    public AuthController(AuthCodeStore codes, JwtTokenService jwt, IConfiguration cfg)
    {
        _codes = codes;
        _jwt = jwt;
        _cfg = cfg;
    }

    [HttpPost("send-code")]
    public ActionResult<SendCodeResponse> SendCode([FromBody] SendCodeRequest req)
    {
        var email = (req.Email ?? "").Trim();
        if (string.IsNullOrWhiteSpace(email) || !email.Contains('@'))
            return BadRequest(new ErrorEnvelope(new ApiError("validation_error", "Email is invalid")));

        var ttl = _cfg.GetValue("AuthCode:CodeTtlSeconds", 300);

        var (requestId, expiresInSec, code) = _codes.CreateCode(email, ttl);

        // MVP: вместо реальной почты печатаем код в консоль
        Console.WriteLine($"[AUTH] Code for {email}: {code} (requestId={requestId})");

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
