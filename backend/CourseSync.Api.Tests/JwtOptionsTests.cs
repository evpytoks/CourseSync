using System.ComponentModel.DataAnnotations;
using CourseSync.Api.Infrastructure;
using Microsoft.Extensions.Configuration;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class JwtOptionsTests
{
    [Fact]
    public void Appsettings_json_has_valid_jwt_options()
    {
        var repoRoot = FindRepoRoot();
        var apiDir = Path.Combine(repoRoot, "backend", "CourseSync.Api");
        var cfg = new ConfigurationBuilder()
            .SetBasePath(apiDir)
            .AddJsonFile("appsettings.json", optional: false)
            .Build();
        var opt = new JwtOptions();
        cfg.GetSection(JwtOptions.SectionName).Bind(opt);
        Validator.ValidateObject(opt, new ValidationContext(opt), validateAllProperties: true);
    }

    [Fact]
    public void Missing_required_keys_fail_validation()
    {
        var cfg = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?> { ["Jwt:AccessTokenMinutes"] = "60" })
            .Build();
        var opt = new JwtOptions();
        cfg.GetSection(JwtOptions.SectionName).Bind(opt);
        Assert.Throws<ValidationException>(() =>
            Validator.ValidateObject(opt, new ValidationContext(opt), validateAllProperties: true));
    }

    [Fact]
    public void AccessTokenMinutes_out_of_range_fails_validation()
    {
        var opt = new JwtOptions
        {
            Issuer = "T", Audience = "T", Key = "12345678901234567890123456789012", AccessTokenMinutes = 0
        };
        Assert.Throws<ValidationException>(() =>
            Validator.ValidateObject(opt, new ValidationContext(opt), validateAllProperties: true));
    }

    private static string FindRepoRoot()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir != null)
        {
            if (File.Exists(Path.Combine(dir.FullName, "CourseSync.sln"))) return dir.FullName;
            dir = dir.Parent;
        }
        throw new InvalidOperationException("Repo root not found.");
    }
}
