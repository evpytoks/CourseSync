using Amazon.S3;
using Amazon.S3.Model;
using Microsoft.Extensions.Options;

namespace CourseSync.Api.Infrastructure.Storage;

public sealed class S3CourseMaterialBlobStorage : ICourseMaterialBlobStorage
{
    private readonly IAmazonS3 _s3;
    private readonly string _bucket;

    public S3CourseMaterialBlobStorage(IAmazonS3 s3, IOptions<CourseMaterialStorageOptions> options)
    {
        _s3 = s3;
        _bucket = options.Value.S3?.BucketName
                  ?? throw new InvalidOperationException("CourseMaterials:S3:BucketName is missing.");
    }

    public async Task UploadAsync(Stream content, string objectKey, CancellationToken ct)
    {
        var request = new PutObjectRequest
        {
            BucketName = _bucket,
            Key = objectKey,
            InputStream = content,
            ContentType = "application/pdf",
            AutoCloseStream = false
        };

        await _s3.PutObjectAsync(request, ct);
    }

    public Task DeleteAsync(string objectKey, CancellationToken ct) =>
        _s3.DeleteObjectAsync(_bucket, objectKey, ct);
}
