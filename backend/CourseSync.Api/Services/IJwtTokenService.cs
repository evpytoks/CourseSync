namespace CourseSync.Api.Services;

public interface IJwtTokenService
{
    string CreateToken(Guid userId, string email, int tokenVersion);
}
