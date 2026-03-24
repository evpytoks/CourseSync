namespace CourseSync.Api.Infrastructure.Storage;

public sealed class LocalCourseMaterialBlobStorage : ICourseMaterialBlobStorage
{
    private readonly IWebHostEnvironment _env;

    public LocalCourseMaterialBlobStorage(IWebHostEnvironment env)
    {
        _env = env;
    }

    private string StorageRoot => Path.Combine(_env.ContentRootPath, "course-materials");

    public async Task UploadAsync(Stream content, string objectKey, CancellationToken ct)
    {
        var fullPath = Path.Combine(StorageRoot, objectKey.Replace('/', Path.DirectorySeparatorChar));
        Directory.CreateDirectory(Path.GetDirectoryName(fullPath)!);
        await using var outStream = new FileStream(fullPath, FileMode.CreateNew, FileAccess.Write);
        await content.CopyToAsync(outStream, ct);
    }

    public Task DeleteAsync(string objectKey, CancellationToken ct)
    {
        var fullPath = Path.Combine(StorageRoot, objectKey.Replace('/', Path.DirectorySeparatorChar));
        TryDeleteFile(fullPath);
        return Task.CompletedTask;
    }

    private static void TryDeleteFile(string fullPath)
    {
        try
        {
            if (File.Exists(fullPath))
                File.Delete(fullPath);
        }
        catch
        {
        }
    }
}
