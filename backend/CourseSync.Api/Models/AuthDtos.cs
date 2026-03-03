using System.Text.Json.Serialization;

namespace CourseSync.Api.Models;

public sealed record SendCodeRequest(string Email);
public sealed record SendCodeResponse(string RequestId, DateTimeOffset ExpiresAt);

public sealed record LoginRequest(string Email, string RequestId, string Code);

public sealed record LoginResponse(string Token, string RefreshToken);

public sealed record RefreshRequest(string RefreshToken);
public sealed record RefreshResponse(string Token, string RefreshToken);

public sealed record ApiError([property: JsonPropertyName("error_code")] string Code);
public sealed record ErrorEnvelope(ApiError Error);
