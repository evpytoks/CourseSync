using Amazon;
using Amazon.S3;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Options;

namespace CourseSync.Api.Infrastructure.Storage;

public static class CourseMaterialStorageServiceCollectionExtensions
{
    public static IServiceCollection AddCourseMaterialBlobStorage(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        services.AddOptions<CourseMaterialStorageOptions>()
            .Bind(configuration.GetSection(CourseMaterialStorageOptions.SectionName))
            .Validate(o =>
            {
                CourseMaterialStorageOptions.Validate(o);
                return true;
            }, "Invalid CourseMaterials configuration")
            .ValidateOnStart();

        services.AddSingleton<IAmazonS3>(sp =>
        {
            var o = sp.GetRequiredService<IOptions<CourseMaterialStorageOptions>>().Value;
            if (o.Kind != CourseMaterialStorageKind.S3 || o.S3 is null)
                throw new InvalidOperationException("IAmazonS3 is only registered for S3 storage; use CourseMaterials:Kind = S3.");

            var s = o.S3;
            var cfg = new AmazonS3Config
            {
                ServiceURL = s.ServiceUrl.TrimEnd('/'),
                ForcePathStyle = s.ForcePathStyle
            };

            if (!string.IsNullOrWhiteSpace(s.Region))
                cfg.AuthenticationRegion = s.Region;
            else
                cfg.AuthenticationRegion = RegionEndpoint.USEast1.SystemName;

            return new AmazonS3Client(s.AccessKey, s.SecretKey, cfg);
        });

        services.AddScoped<LocalCourseMaterialBlobStorage>();
        services.AddScoped<S3CourseMaterialBlobStorage>();
        services.AddScoped<ICourseMaterialBlobStorage>(sp =>
        {
            var opts = sp.GetRequiredService<IOptions<CourseMaterialStorageOptions>>().Value;
            return opts.Kind == CourseMaterialStorageKind.S3
                ? sp.GetRequiredService<S3CourseMaterialBlobStorage>()
                : sp.GetRequiredService<LocalCourseMaterialBlobStorage>();
        });

        return services;
    }
}
