using System.Text;
using System.Threading.RateLimiting;
using CourseSync.Api.Application.Courses;
using CourseSync.Api.Data;
using CourseSync.Api.Infrastructure.Email;
using CourseSync.Api.Infrastructure.Json;
using CourseSync.Api.Infrastructure.Push;
using CourseSync.Api.Infrastructure.Storage;
using CourseSync.Api.Services;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Http.Features;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;

namespace CourseSync.Api.Infrastructure.Composition;

public static class CompositionRootExtensions
{
    public static void AddCourseSyncPresentation(this WebApplicationBuilder builder)
    {
        const long maxMultipartBodyBytes = CourseMaterialUploadLimits.MaxMultipartRequestBytes;

        builder.Services.Configure<FormOptions>(o =>
        {
            o.MultipartBodyLengthLimit = maxMultipartBodyBytes;
        });

        builder.WebHost.ConfigureKestrel(o =>
        {
            o.Limits.MaxRequestBodySize = maxMultipartBodyBytes;
        });

        builder.Services.AddControllers()
            .AddJsonOptions(o =>
            {
                o.JsonSerializerOptions.PropertyNameCaseInsensitive = true;
                o.JsonSerializerOptions.Converters.Add(new UtcIsoDateTimeConverter());
                o.JsonSerializerOptions.Converters.Add(new UtcIsoDateTimeOffsetConverter());
            });

        builder.Services.AddEndpointsApiExplorer();
        builder.Services.AddSwaggerGen(o =>
        {
            o.AddSecurityDefinition("Bearer", new Microsoft.OpenApi.Models.OpenApiSecurityScheme
            {
                In = Microsoft.OpenApi.Models.ParameterLocation.Header,
                Name = "Authorization",
                Type = Microsoft.OpenApi.Models.SecuritySchemeType.Http,
                Scheme = "Bearer",
                Description = "JWT access token. Example: Bearer &lt;token&gt;"
            });
            o.AddSecurityRequirement(new Microsoft.OpenApi.Models.OpenApiSecurityRequirement
            {
                {
                    new Microsoft.OpenApi.Models.OpenApiSecurityScheme
                    {
                        Reference = new Microsoft.OpenApi.Models.OpenApiReference
                        {
                            Type = Microsoft.OpenApi.Models.ReferenceType.SecurityScheme,
                            Id = "Bearer"
                        }
                    },
                    Array.Empty<string>()
                }
            });
        });
    }

    public static void AddCourseSyncServices(this WebApplicationBuilder builder)
    {
        var cs = builder.Configuration.GetConnectionString("AppDb")
                 ?? throw new InvalidOperationException("ConnectionStrings:AppDb is missing");
        builder.Services.AddDbContext<AppDbContext>(o => o.UseNpgsql(cs));

        builder.Services.AddScoped<IUserService, UserService>();
        builder.Services.AddScoped<TokenVersionJwtBearerEvents>();
        builder.Services.AddHostedService<AuthCleanupHostedService>();
        builder.Services.AddHostedService<NotificationDispatcherHostedService>();

        builder.Services
            .AddOptions<AuthCodeOptions>()
            .Bind(builder.Configuration.GetSection(AuthCodeOptions.SectionName))
            .ValidateDataAnnotations()
            .ValidateOnStart();

        builder.Services
            .AddOptions<AuthTokensOptions>()
            .Bind(builder.Configuration.GetSection(AuthTokensOptions.SectionName))
            .ValidateDataAnnotations()
            .ValidateOnStart();

        builder.Services
            .AddOptions<SmtpOptions>()
            .Bind(builder.Configuration.GetSection(SmtpOptions.SectionName))
            .Validate(o =>
            {
                if (!o.Enabled) return true;

                return !string.IsNullOrWhiteSpace(o.Host)
                       && o.Port > 0
                       && !string.IsNullOrWhiteSpace(o.FromEmail);
            }, "Invalid SMTP configuration");

        builder.Services
            .AddOptions<FcmOptions>()
            .Bind(builder.Configuration.GetSection(FcmOptions.SectionName));

        builder.Services.AddSingleton<IEmailSender, MailKitEmailSender>();

        builder.Services.AddScoped<IAuthLoginCodeService, AuthLoginCodeService>();
        builder.Services.AddScoped<IRefreshTokenService, RefreshTokenService>();
        builder.Services.AddScoped<IGroupService, GroupService>();
        builder.Services.AddScoped<ICourseQueryService, CourseQueryService>();
        builder.Services.AddScoped<ICourseCommandService, CourseCommandService>();
        builder.Services.AddScoped<ICourseGradingService, CourseGradingService>();
        builder.Services.AddScoped<ICourseCumulativeGradeService, CourseCumulativeGradeService>();
        builder.Services.AddCourseMaterialBlobStorage(builder.Configuration);
        builder.Services.AddScoped<ICourseMaterialService, CourseMaterialService>();
        builder.Services.AddScoped<NotificationService>();
        builder.Services.AddScoped<ICalendarService, CalendarService>();
        builder.Services.AddScoped<INewsService, NewsService>();
        builder.Services.AddScoped<IUserDeviceService, UserDeviceService>();
        builder.Services.AddHttpClient<FcmPushSender>();
        builder.Services.AddSingleton<IPushSender>(sp => sp.GetRequiredService<FcmPushSender>());
        builder.Services.AddSingleton<IJwtTokenService, JwtTokenService>();
    }

    public static void AddCourseSyncAuth(this WebApplicationBuilder builder)
    {
        builder.Services
            .AddOptions<JwtOptions>()
            .Bind(builder.Configuration.GetSection(JwtOptions.SectionName))
            .ValidateDataAnnotations()
            .ValidateOnStart();

        var jwtSection = builder.Configuration.GetSection(JwtOptions.SectionName);
        var jwtKey = jwtSection["Key"] ?? throw new InvalidOperationException("Jwt:Key is required.");
        var jwtIssuer = jwtSection["Issuer"] ?? throw new InvalidOperationException("Jwt:Issuer is required.");
        var jwtAudience = jwtSection["Audience"] ?? throw new InvalidOperationException("Jwt:Audience is required.");

        builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
            .AddJwtBearer(o =>
            {
                o.EventsType = typeof(TokenVersionJwtBearerEvents);
                o.TokenValidationParameters = new TokenValidationParameters
                {
                    ValidateIssuer = true,
                    ValidateAudience = true,
                    ValidateLifetime = true,
                    ValidateIssuerSigningKey = true,
                    ValidIssuer = jwtIssuer,
                    ValidAudience = jwtAudience,
                    IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtKey)),
                    ClockSkew = TimeSpan.FromSeconds(10)
                };
            });

        builder.Services.AddAuthorization();
    }

    public static void AddCourseSyncRateLimiting(this WebApplicationBuilder builder)
    {
        var rateLimit = builder.Configuration.GetSection("RateLimit");
        var globalPermit = rateLimit.GetValue("GlobalPermitLimit", 100);
        var globalWindowMin = rateLimit.GetValue("GlobalWindowMinutes", 1);

        var endpointsSection = rateLimit.GetSection("Endpoints");
        var endpointLimits = new Dictionary<string, (int PermitLimit, int WindowMinutes)>(StringComparer.OrdinalIgnoreCase);
        foreach (var child in endpointsSection.GetChildren())
        {
            var permit = child.GetValue("PermitLimit", globalPermit);
            var window = child.GetValue("WindowMinutes", globalWindowMin);
            endpointLimits[child.Key.TrimStart('/')] = (permit, window);
        }

        builder.Services.AddRateLimiter(options =>
        {
            options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
            options.OnRejected = async (context, ct) =>
            {
                context.HttpContext.Response.ContentType = "application/json";
                if (context.Lease.TryGetMetadata(MetadataName.RetryAfter, out var retryAfter))
                    context.HttpContext.Response.Headers.RetryAfter = ((int)retryAfter.TotalSeconds).ToString();
                await context.HttpContext.Response.WriteAsJsonAsync(new { error = new { error_code = "rate_limited" } }, ct);
            };
            options.GlobalLimiter = PartitionedRateLimiter.Create<HttpContext, string>(httpContext =>
            {
                var ip = httpContext.Connection.RemoteIpAddress?.ToString() ?? "unknown";
                var path = (httpContext.Request.Path.Value ?? "").TrimStart('/');
                var endpointKey = "default";
                var bestLength = 0;
                foreach (var key in endpointLimits.Keys)
                {
                    var matches = false;
                    if (key.Contains("{id}", StringComparison.OrdinalIgnoreCase))
                    {
                        var pathSegments = path.Split('/', StringSplitOptions.RemoveEmptyEntries);
                        var keySegments = key.Split('/', StringSplitOptions.RemoveEmptyEntries);
                        if (pathSegments.Length == keySegments.Length)
                        {
                            matches = true;
                            for (var i = 0; i < keySegments.Length; i++)
                            {
                                if (!string.Equals(keySegments[i], "{id}", StringComparison.OrdinalIgnoreCase)
                                    && !string.Equals(pathSegments[i], keySegments[i], StringComparison.OrdinalIgnoreCase))
                                {
                                    matches = false;
                                    break;
                                }
                            }
                        }
                    }
                    else if (path.Equals(key, StringComparison.OrdinalIgnoreCase) || path.StartsWith(key + "/", StringComparison.OrdinalIgnoreCase))
                    {
                        matches = true;
                    }
                    if (matches && key.Length > bestLength)
                    {
                        endpointKey = key;
                        bestLength = key.Length;
                    }
                }
                var partitionKey = $"{ip}:{endpointKey}";
                var (permitLimit, windowMin) = endpointKey == "default"
                    ? (globalPermit, globalWindowMin)
                    : endpointLimits[endpointKey];
                return RateLimitPartition.GetFixedWindowLimiter(partitionKey, _ => new FixedWindowRateLimiterOptions
                {
                    PermitLimit = permitLimit,
                    Window = TimeSpan.FromMinutes(windowMin),
                    QueueLimit = 0,
                    AutoReplenishment = true
                });
            });
        });
    }

    public static void ApplyCourseSyncMigrations(this WebApplication app)
    {
        using var scope = app.Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        var logger = scope.ServiceProvider.GetRequiredService<ILoggerFactory>().CreateLogger("DatabaseMigration");
        logger.LogInformation("Applying database migrations...");
        db.Database.Migrate();
        logger.LogInformation("Database migrations applied successfully.");
    }

    public static void UseCourseSyncPipeline(this WebApplication app)
    {
        app.UseSwagger();
        app.UseSwaggerUI();

        if (app.Environment.IsDevelopment())
            app.UseDeveloperExceptionPage();

        app.UseRateLimiter();
        app.UseAuthentication();
        app.UseAuthorization();

        app.Use(async (context, next) =>
        {
            await next();

            var status = context.Response.StatusCode;
            if (status >= 400)
            {
                var loggerFactory = context.RequestServices.GetRequiredService<ILoggerFactory>();
                var logger = loggerFactory.CreateLogger("HttpStatusLogger");

                logger.LogWarning("HTTP {StatusCode} {Method} {Path}",
                    status,
                    context.Request.Method,
                    context.Request.Path);
            }
        });
    }
}
