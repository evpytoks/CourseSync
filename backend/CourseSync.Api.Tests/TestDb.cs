using CourseSync.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Tests;

public sealed class TestDb : IAsyncDisposable
{
    public AppDbContext Db { get; }

    public TestDb()
    {
        var opt = new DbContextOptionsBuilder<AppDbContext>()
            .UseInMemoryDatabase("coursesync_test_" + Guid.NewGuid())
            .EnableSensitiveDataLogging()
            .Options;

        Db = new AppDbContext(opt);
        Db.Database.EnsureCreated();
    }

    public async ValueTask DisposeAsync()
    {
        await Db.DisposeAsync();
    }
}
