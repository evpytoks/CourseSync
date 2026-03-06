using System.ComponentModel.DataAnnotations;

namespace CourseSync.Api.Infrastructure;

public sealed class JwtOptions
{
    public const string SectionName = "Jwt";

    [Required]
    [MinLength(1)]
    public string Issuer { get; set; } = null!;

    [Required]
    [MinLength(1)]
    public string Audience { get; set; } = null!;

    [Required]
    [MinLength(16)]
    public string Key { get; set; } = null!;

    [Range(1, 10080)]
    public int AccessTokenMinutes { get; set; } = 60;
}
