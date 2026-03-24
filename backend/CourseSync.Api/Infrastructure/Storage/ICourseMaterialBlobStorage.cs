namespace CourseSync.Api.Infrastructure.Storage;

public interface ICourseMaterialBlobStorage
{
    Task UploadAsync(Stream content, string objectKey, CancellationToken ct);

    Task DeleteAsync(string objectKey, CancellationToken ct);
}
