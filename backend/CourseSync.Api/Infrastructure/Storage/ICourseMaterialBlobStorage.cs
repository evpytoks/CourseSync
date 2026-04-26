namespace CourseSync.Api.Infrastructure.Storage;

public interface ICourseMaterialBlobStorage
{
    Task EnsureAvailableAsync(CancellationToken ct);

    Task UploadAsync(Stream content, string objectKey, CancellationToken ct);

    Task DeleteAsync(string objectKey, CancellationToken ct);

    Task<Stream?> OpenReadAsync(string objectKey, CancellationToken ct);
}
