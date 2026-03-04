using System.Text;
using System.Threading.RateLimiting;
using CourseSync.Api.Services;
using CourseSync.Api.Infrastructure.Email;
using CourseSync.Api.Infrastructure;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;
using CourseSync.Api.Data;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var cs = builder.Configuration.GetConnectionString("AppDb")
         ?? throw new InvalidOperationException("ConnectionStrings:AppDb is missing");
builder.Services.AddDbContext<AppDbContext>(o => o.UseNpgsql(cs));
builder.Services.AddScoped<UserService>();
builder.Services.AddScoped<TokenVersionJwtBearerEvents>();
builder.Services.AddHostedService<AuthCleanupHostedService>();

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

builder.Services.AddSingleton<IEmailSender, MailKitEmailSender>();

builder.Services.AddScoped<AuthLoginCodeService>();
builder.Services.AddScoped<RefreshTokenService>();
builder.Services.AddSingleton<JwtTokenService>();

var jwt = builder.Configuration.GetSection("Jwt");

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

            ValidIssuer = jwt["Issuer"],
            ValidAudience = jwt["Audience"],
            IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwt["Key"]!)),
            ClockSkew = TimeSpan.FromSeconds(10)
        };
    });

builder.Services.AddAuthorization();

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
            if ((path.Equals(key, StringComparison.OrdinalIgnoreCase) || path.StartsWith(key + "/", StringComparison.OrdinalIgnoreCase))
                && key.Length > bestLength)
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

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseDeveloperExceptionPage();
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseAuthentication();
app.UseAuthorization();
app.UseRateLimiter();

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

app.MapControllers();

app.Run();
