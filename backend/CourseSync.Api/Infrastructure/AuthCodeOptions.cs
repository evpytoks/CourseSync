using System.ComponentModel.DataAnnotations;

namespace CourseSync.Api.Infrastructure;

public sealed class AuthCodeOptions
{
    public const string SectionName = "AuthCode";

    [Range(30, 600)]
    public int CodeTtlSeconds { get; set; }

    [Range(10, 3600)]
    public int SendCooldownSeconds { get; set; } = 60;

    [Range(1, 5)]
    public int MaxAttempts { get; set; }

    [Range(10, 86400)]
    public int LockoutSeconds { get; set; }

    [Required]
    [MinLength(16)]
    public string HashKey { get; set; } = null!;
}
