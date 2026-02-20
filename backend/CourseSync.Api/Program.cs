using System.Text;
using CourseSync.Api.Services;
using CourseSync.Api.Infrastructure.Email;
using CourseSync.Api.Infrastructure;
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

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseDeveloperExceptionPage();
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();
