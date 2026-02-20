using System.ComponentModel.DataAnnotations;

namespace CourseSync.Api.Infrastructure;

public sealed class AuthTokensOptions
{
    public const string SectionName = "AuthTokens";

    [Range(1, 365)]
    public int RefreshTokenTtlDays { get; set; }

    [Required]
    [MinLength(16)]
    public string RefreshTokenHashKey { get; set; } = null!;
}

