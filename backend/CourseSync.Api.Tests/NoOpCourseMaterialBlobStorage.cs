using CourseSync.Api.Infrastructure.Storage;

namespace CourseSync.Api.Tests;

internal sealed class NoOpCourseMaterialBlobStorage : ICourseMaterialBlobStorage
{
    public Task EnsureAvailableAsync(CancellationToken ct) => Task.CompletedTask;

    public Task UploadAsync(Stream content, string objectKey, CancellationToken ct) => Task.CompletedTask;

    public Task DeleteAsync(string objectKey, CancellationToken ct) => Task.CompletedTask;

    public Task<Stream?> OpenReadAsync(string objectKey, CancellationToken ct)
    {
        var bytes = System.Text.Encoding.ASCII.GetBytes("%PDF-1.4\n%noop\n");
        Stream s = new MemoryStream(bytes, writable: false);
        return Task.FromResult<Stream?>(s);
    }
}
