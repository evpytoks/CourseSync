using System.Text;
using CourseSync.Api.Services;
using CourseSync.Api.Infrastructure.Email;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

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

builder.Services.AddSingleton<MailKitEmailSender>();

var smtp = builder.Configuration.GetSection("Smtp").Get<SmtpOptions>();

if (smtp is null || !smtp.Enabled)
    throw new InvalidOperationException("SMTP is disabled. Email auth requires SMTP configuration.");

builder.Services.AddSingleton<IEmailSender, MailKitEmailSender>();

builder.Services.AddSingleton<AuthCodeStore>();
builder.Services.AddSingleton<JwtTokenService>();

var jwt = builder.Configuration.GetSection("Jwt");

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(o =>
    {
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
