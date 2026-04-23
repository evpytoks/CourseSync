using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Data;

public sealed partial class AppDbContext : DbContext
{
    public AppDbContext(DbContextOptions<AppDbContext> options) : base(options) {}

    public DbSet<User> Users => Set<User>();
    public DbSet<AuthLoginRequest> AuthLoginRequests => Set<AuthLoginRequest>();
    public DbSet<RefreshToken> RefreshTokens => Set<RefreshToken>();
    public DbSet<Group> Groups => Set<Group>();
    public DbSet<GroupMember> GroupMembers => Set<GroupMember>();
    public DbSet<GroupMemberBlock> GroupMemberBlocks => Set<GroupMemberBlock>();
    public DbSet<Course> Courses => Set<Course>();
    public DbSet<CourseGradingElement> CourseGradingElements => Set<CourseGradingElement>();
    public DbSet<CourseGradingScore> CourseGradingScores => Set<CourseGradingScore>();
    public DbSet<CourseCumulativeGrade> CourseCumulativeGrades => Set<CourseCumulativeGrade>();
    public DbSet<CourseCumulativeGradeElement> CourseCumulativeGradeElements => Set<CourseCumulativeGradeElement>();
    public DbSet<CourseGeneralMaterial> CourseGeneralMaterials => Set<CourseGeneralMaterial>();
    public DbSet<CoursePersonalMaterial> CoursePersonalMaterials => Set<CoursePersonalMaterial>();
    public DbSet<CalendarEvent> CalendarEvents => Set<CalendarEvent>();
    public DbSet<CalendarEventUserState> CalendarEventUserStates => Set<CalendarEventUserState>();
    public DbSet<Notification> Notifications => Set<Notification>();
    public DbSet<UserDevice> UserDevices => Set<UserDevice>();
    public DbSet<News> News => Set<News>();
    public DbSet<NewsRead> NewsReads => Set<NewsRead>();

    protected override void OnModelCreating(ModelBuilder b) => ConfigureModel(b);
}
