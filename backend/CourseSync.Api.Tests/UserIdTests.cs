using CourseSync.Api.Services;
using Xunit;

namespace CourseSync.Api.Tests;

public sealed class UserIdTests
{
    [Fact]
    public void StableUserId_is_deterministic_for_same_email()
    {
        Assert.Equal(UserId.StableUserId("user@edu.hse.ru"), UserId.StableUserId("user@edu.hse.ru"));
    }

    [Fact]
    public void StableUserId_different_emails_produce_different_ids()
    {
        Assert.NotEqual(UserId.StableUserId("user@edu.hse.ru"), UserId.StableUserId("owner@edu.hse.ru"));
    }

    [Fact]
    public void StableUserId_normalizes_whitespace_and_case()
    {
        Assert.Equal(UserId.StableUserId("User@Edu.HSE.Ru"), UserId.StableUserId("  user@edu.hse.ru  "));
    }
}
