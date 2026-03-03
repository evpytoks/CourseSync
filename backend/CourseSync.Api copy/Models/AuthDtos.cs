using System.Text.Json.Serialization;

namespace CourseSync.Api.Models;

public sealed record SendCodeRequest(string Email);
public sealed record SendCodeResponse(string RequestId, int ExpiresInSec);

public sealed record LoginRequest(string Email, string RequestId, string Code);

public sealed record UserDto(Guid Id, string Email);
public sealed record LoginResponse(string Token, string RefreshToken, UserDto User);

public sealed record RefreshRequest(string RefreshToken);
public sealed record RefreshResponse(string Token, string RefreshToken);

public sealed record ApiError([property: JsonPropertyName("error_code")] string Code, string Message);
public sealed record ErrorEnvelope(ApiError Error);
