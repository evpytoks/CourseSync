using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using CourseSync.Api.Infrastructure;
using CourseSync.Api.Services;
using Microsoft.Extensions.Options;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class JwtTokenServiceTests
{
    [Fact]
    public void CreateToken_produces_valid_jwt_with_expected_claims()
    {
        var opt = Options.Create(new JwtOptions
        {
            Issuer = "TestIssuer",
            Audience = "TestAudience",
            Key = "SUPER_LONG_SECRET_KEY_FOR_TESTING_32",
            AccessTokenMinutes = 60
        });
        var svc = new JwtTokenService(opt);
        var userId = Guid.NewGuid();
        var email = "user@edu.hse.ru";
        var tokenVersion = 2;
        var token = svc.CreateToken(userId, email, tokenVersion);
        Assert.False(string.IsNullOrWhiteSpace(token));
        var handler = new JwtSecurityTokenHandler();
        var jwt = handler.ReadJwtToken(token);
        Assert.Equal("TestIssuer", jwt.Issuer);
        Assert.Contains(jwt.Audiences, a => a == "TestAudience");
        Assert.Equal(userId.ToString(), jwt.Subject);
        Assert.Equal(userId.ToString(), jwt.Claims.Single(c => c.Type == "uid").Value);
        Assert.Equal(tokenVersion.ToString(), jwt.Claims.Single(c => c.Type == "tv").Value);
        Assert.True(jwt.ValidTo > DateTime.UtcNow);
    }
}
