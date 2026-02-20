using System.ComponentModel.DataAnnotations;

namespace CourseSync.Api.Infrastructure;

public sealed class AuthCodeOptions
{
    public const string SectionName = "AuthCode";

    [Range(30, 600)]
    public int CodeTtlSeconds { get; set; }

    [Range(1, 5)]
    public int MaxAttempts { get; set; }

    [Required]
    [MinLength(16)]
    public string HashKey { get; set; } = null!;
}

