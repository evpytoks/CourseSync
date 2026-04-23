using CourseSync.Api.Infrastructure;
using CourseSync.Api.Infrastructure.Composition;

var builder = WebApplication.CreateBuilder(args);

builder.AddCourseSyncPresentation();
builder.AddCourseSyncServices();
builder.AddCourseSyncAuth();
builder.AddCourseSyncRateLimiting();

var app = builder.Build();

app.ApplyCourseSyncMigrations();
app.UseCourseSyncPipeline();

app.MapControllers();

app.Run();
