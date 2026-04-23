namespace CourseSync.Api.Services;

public interface INewsService
{
    Task<List<NewsService.NewsListDto>> GetAllForUserAsync(Guid userId, CancellationToken ct);
    Task<int> GetUnreadCountAsync(Guid userId, CancellationToken ct);
    Task<(bool Ok, NewsService.NewsDetailsDto? Item, string? ErrorCode)> GetByIdAsync(Guid userId, Guid newsId, CancellationToken ct);
    Task<int> MarkAllReadAsync(Guid userId, CancellationToken ct);
    Task<(bool Ok, string? ErrorCode)> CheckOwnerAndCreateNewsAsync(Guid userId, Guid groupId, string text, CancellationToken ct);
}
