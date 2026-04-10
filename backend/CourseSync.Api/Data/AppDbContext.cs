using Microsoft.EntityFrameworkCore;

namespace CourseSync.Api.Data;

public sealed class AppDbContext : DbContext
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

    protected override void OnModelCreating(ModelBuilder b)
    {
        b.Entity<User>(e =>
        {
            e.ToTable("users");
            e.HasKey(x => x.Id);

            e.Property(x => x.Email).IsRequired();
            e.HasIndex(x => x.Email).IsUnique();

            e.Property(x => x.TokenVersion)
                .HasColumnName("token_version")
                .HasDefaultValue(0)
                .IsRequired();

            e.Property(x => x.AuthCodeLastSentAt)
                .HasColumnName("auth_code_last_sent_at");

            e.Property(x => x.CurrentGroupId)
                .HasColumnName("current_group_id");

            e.Property(x => x.NotificationsOn)
                .HasColumnName("notifications_on")
                .HasDefaultValue(false)
                .IsRequired();

            e.Property(x => x.DarkThemeOn)
                .HasColumnName("dark_theme_on")
                .HasDefaultValue(false)
                .IsRequired();

            e.Property(x => x.CalendarEventTypeColors)
                .HasColumnName("calendar_event_type_colors")
                .HasDefaultValue("")
                .IsRequired()
                .HasMaxLength(4000);

            e.HasOne<Group>()
                .WithMany()
                .HasForeignKey(x => x.CurrentGroupId)
                .OnDelete(DeleteBehavior.SetNull);
        });

        b.Entity<AuthLoginRequest>(e =>
        {
            e.ToTable("auth_login_requests");
            e.HasKey(x => x.RequestId);

            e.Property(x => x.RequestId).HasColumnName("request_id").IsRequired();
            e.Property(x => x.UserId).HasColumnName("user_id").IsRequired();
            e.Property(x => x.Email).HasColumnName("email").IsRequired();
            e.Property(x => x.CodeHash).HasColumnName("code_hash").IsRequired();
            e.Property(x => x.CreatedAt).HasColumnName("created_at").IsRequired();
            e.Property(x => x.ExpiresAt).HasColumnName("expires_at").IsRequired();
            e.Property(x => x.AttemptCount).HasColumnName("attempt_count").IsRequired();
            e.Property(x => x.UsedAt).HasColumnName("used_at");

            e.HasOne(x => x.User)
                .WithMany()
                .HasForeignKey(x => x.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            e.Property(x => x.xmin)
                .HasColumnName("xmin")
                .HasColumnType("xid")
                .ValueGeneratedOnAddOrUpdate()
                .IsRowVersion();

            e.HasIndex(x => x.Email);
            e.HasIndex(x => x.ExpiresAt);
        });

        b.Entity<RefreshToken>(e =>
        {
            e.ToTable("refresh_tokens");
            e.HasKey(x => x.Id);

            e.Property(x => x.Id).HasColumnName("id").IsRequired();
            e.Property(x => x.UserId).HasColumnName("user_id").IsRequired();
            e.Property(x => x.TokenHash).HasColumnName("token_hash").IsRequired();
            e.Property(x => x.CreatedAt).HasColumnName("created_at").IsRequired();
            e.Property(x => x.ExpiresAt).HasColumnName("expires_at").IsRequired();
            e.Property(x => x.UsedAt).HasColumnName("used_at");
            e.Property(x => x.RevokedAt).HasColumnName("revoked_at");
            e.Property(x => x.ReplacedByTokenId).HasColumnName("replaced_by_token_id");

            e.HasOne(x => x.User)
                .WithMany()
                .HasForeignKey(x => x.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            e.Property(x => x.xmin)
                .HasColumnName("xmin")
                .HasColumnType("xid")
                .ValueGeneratedOnAddOrUpdate()
                .IsRowVersion();

            e.HasIndex(x => x.TokenHash).IsUnique();
            e.HasIndex(x => x.UserId);
            e.HasIndex(x => x.ExpiresAt);
        });

        b.Entity<Group>(e =>
        {
            e.ToTable("groups");
            e.HasKey(x => x.Id);

            e.Property(x => x.Name).HasColumnName("name").IsRequired().HasMaxLength(20);
            e.Property(x => x.Code).HasColumnName("code").IsRequired().HasMaxLength(6);
            e.Property(x => x.CodeGeneratedAt).HasColumnName("code_generated_at").IsRequired();
            e.Property(x => x.CreatedAt).HasColumnName("created_at").IsRequired();
            e.Property(x => x.CreatorEmail).HasColumnName("creator_email").IsRequired().HasMaxLength(320);

            e.HasIndex(x => x.Name);
            e.HasIndex(x => x.Code).IsUnique();
        });

        b.Entity<GroupMember>(e =>
        {
            e.ToTable("group_members");
            e.HasKey(x => new { x.GroupId, x.UserId });

            e.Property(x => x.GroupId).HasColumnName("group_id").IsRequired();
            e.Property(x => x.UserId).HasColumnName("user_id").IsRequired();
            e.Property(x => x.Role).HasColumnName("role").IsRequired();
            e.Property(x => x.JoinedAt).HasColumnName("joined_at").IsRequired();

            e.HasOne(x => x.Group)
                .WithMany()
                .HasForeignKey(x => x.GroupId)
                .OnDelete(DeleteBehavior.Cascade);
            e.HasOne(x => x.User)
                .WithMany()
                .HasForeignKey(x => x.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasIndex(x => x.UserId);
        });

        b.Entity<GroupMemberBlock>(e =>
        {
            e.ToTable("group_member_blocks");
            e.HasKey(x => new { x.GroupId, x.UserId });

            e.Property(x => x.GroupId).HasColumnName("group_id").IsRequired();
            e.Property(x => x.UserId).HasColumnName("user_id").IsRequired();
            e.Property(x => x.BlockedAt).HasColumnName("blocked_at").IsRequired();

            e.HasOne(x => x.Group)
                .WithMany()
                .HasForeignKey(x => x.GroupId)
                .OnDelete(DeleteBehavior.Cascade);
            e.HasOne(x => x.User)
                .WithMany()
                .HasForeignKey(x => x.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasIndex(x => x.UserId);
        });

        b.Entity<Course>(e =>
        {
            e.ToTable("courses");
            e.HasKey(x => x.Id);

            e.Property(x => x.GroupId).HasColumnName("group_id").IsRequired();
            e.Property(x => x.Name).HasColumnName("name").IsRequired().HasMaxLength(50);
            e.Property(x => x.GeneralInfo).HasColumnName("general_info").IsRequired().HasMaxLength(2000);
            e.Property(x => x.Contacts).HasColumnName("contacts").IsRequired().HasMaxLength(2000);
            e.Property(x => x.UsefulLinks).HasColumnName("useful_links").IsRequired().HasMaxLength(8000);
            e.Property(x => x.GradingText).HasColumnName("grading_text").IsRequired().HasMaxLength(3000).HasDefaultValue("");
            e.Property(x => x.CreatedAt).HasColumnName("created_at").IsRequired();

            e.HasOne<Group>()
                .WithMany()
                .HasForeignKey(x => x.GroupId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasIndex(x => x.GroupId);
        });

        b.Entity<CourseGradingElement>(e =>
        {
            e.ToTable("course_grading_elements");
            e.HasKey(x => x.Id);

            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.CourseId).HasColumnName("course_id").IsRequired();
            e.Property(x => x.Name).HasColumnName("name").IsRequired().HasMaxLength(50);
            e.Property(x => x.Coefficient).HasColumnName("coefficient").HasColumnType("numeric(5,4)").IsRequired();
            e.Property(x => x.Block).HasColumnName("block").HasColumnType("numeric(6,2)").IsRequired().HasDefaultValue(0m);
            e.Property(x => x.Position).HasColumnName("position").IsRequired();
            e.Property(x => x.CreatedAt).HasColumnName("created_at").IsRequired();

            e.HasOne(x => x.Course)
                .WithMany()
                .HasForeignKey(x => x.CourseId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasIndex(x => x.CourseId);
            e.HasIndex(x => new { x.CourseId, x.Position });
        });

        b.Entity<CourseCumulativeGrade>(e =>
        {
            e.ToTable("course_cumulative_grade");
            e.HasKey(x => x.CourseId);

            e.Property(x => x.CourseId).HasColumnName("course_id");
            e.Property(x => x.Block).HasColumnName("block").HasColumnType("numeric(6,2)");
            e.Property(x => x.AutomaticThreshold).HasColumnName("automatic_threshold").HasColumnType("numeric(6,2)");
            e.Property(x => x.UpdatedAt).HasColumnName("updated_at").IsRequired();

            e.HasOne(x => x.Course)
                .WithOne()
                .HasForeignKey<CourseCumulativeGrade>(x => x.CourseId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        b.Entity<CourseCumulativeGradeElement>(e =>
        {
            e.ToTable("course_cumulative_grade_elements");
            e.HasKey(x => x.Id);

            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.CourseId).HasColumnName("course_id").IsRequired();
            e.Property(x => x.CourseGradingElementId).HasColumnName("course_grading_element_id").IsRequired();
            e.Property(x => x.Position).HasColumnName("position").IsRequired();

            e.HasOne(x => x.CumulativeGrade)
                .WithMany(x => x.Elements)
                .HasForeignKey(x => x.CourseId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasOne(x => x.GradingElement)
                .WithMany()
                .HasForeignKey(x => x.CourseGradingElementId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasIndex(x => x.CourseId);
            e.HasIndex(x => x.CourseGradingElementId);
            e.HasIndex(x => new { x.CourseId, x.Position });
        });

        b.Entity<CourseGradingScore>(e =>
        {
            e.ToTable("course_grading_scores");
            e.HasKey(x => x.Id);

            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.UserId).HasColumnName("user_id").IsRequired();
            e.Property(x => x.CourseGradingElementId).HasColumnName("course_grading_element_id").IsRequired();
            e.Property(x => x.Number).HasColumnName("number").IsRequired();
            e.Property(x => x.Score).HasColumnName("score").HasColumnType("numeric(6,2)").IsRequired().HasDefaultValue(0m);

            e.HasOne(x => x.User)
                .WithMany()
                .HasForeignKey(x => x.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasOne(x => x.Element)
                .WithMany()
                .HasForeignKey(x => x.CourseGradingElementId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasIndex(x => x.UserId);
            e.HasIndex(x => x.CourseGradingElementId);
            e.HasIndex(x => new { x.UserId, x.CourseGradingElementId, x.Number }).IsUnique();
        });

        b.Entity<CourseGeneralMaterial>(e =>
        {
            e.ToTable("course_general_materials");
            e.HasKey(x => x.Id);

            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.CourseId).HasColumnName("course_id").IsRequired();
            e.Property(x => x.Name).HasColumnName("name").IsRequired().HasMaxLength(255);
            e.Property(x => x.AuthorUserId).HasColumnName("author_user_id").IsRequired();
            e.Property(x => x.AuthorEmail).HasColumnName("author_email").IsRequired().HasMaxLength(320);
            e.Property(x => x.StoragePath).HasColumnName("storage_path").IsRequired().HasMaxLength(512);
            e.Property(x => x.CreatedAt).HasColumnName("created_at").IsRequired();

            e.HasOne(x => x.Course)
                .WithMany()
                .HasForeignKey(x => x.CourseId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasOne<User>()
                .WithMany()
                .HasForeignKey(x => x.AuthorUserId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasIndex(x => x.CourseId);
            e.HasIndex(x => x.CreatedAt);
        });

        b.Entity<CoursePersonalMaterial>(e =>
        {
            e.ToTable("course_personal_materials");
            e.HasKey(x => x.Id);

            e.Property(x => x.Id).HasColumnName("id");
            e.Property(x => x.CourseId).HasColumnName("course_id").IsRequired();
            e.Property(x => x.Name).HasColumnName("name").IsRequired().HasMaxLength(255);
            e.Property(x => x.AuthorUserId).HasColumnName("author_user_id").IsRequired();
            e.Property(x => x.AuthorEmail).HasColumnName("author_email").IsRequired().HasMaxLength(320);
            e.Property(x => x.StoragePath).HasColumnName("storage_path").IsRequired().HasMaxLength(512);
            e.Property(x => x.CreatedAt).HasColumnName("created_at").IsRequired();

            e.HasOne(x => x.Course)
                .WithMany()
                .HasForeignKey(x => x.CourseId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasOne<User>()
                .WithMany()
                .HasForeignKey(x => x.AuthorUserId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasIndex(x => x.CourseId);
            e.HasIndex(x => x.CreatedAt);
        });

        b.Entity<CalendarEvent>(e =>
        {
            e.ToTable("calendar_events");
            e.HasKey(x => x.Id);

            e.Property(x => x.GroupId).HasColumnName("group_id").IsRequired();
            e.Property(x => x.CourseId).HasColumnName("course_id");
            e.Property(x => x.EventType).HasColumnName("event_type").IsRequired().HasMaxLength(30);
            e.Property(x => x.Name).HasColumnName("name").IsRequired().HasMaxLength(50);
            e.Property(x => x.Date).HasColumnName("date").IsRequired();
            e.Property(x => x.Description).HasColumnName("description").IsRequired().HasMaxLength(1000);

            e.HasOne<Group>()
                .WithMany()
                .HasForeignKey(x => x.GroupId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasOne<Course>()
                .WithMany()
                .HasForeignKey(x => x.CourseId)
                .OnDelete(DeleteBehavior.SetNull);

            e.HasIndex(x => x.GroupId);
            e.HasIndex(x => x.CourseId);
            e.HasIndex(x => x.Date);
        });

        b.Entity<CalendarEventUserState>(e =>
        {
            e.ToTable("calendar_event_user_states");
            e.HasKey(x => new { x.EventId, x.UserId });

            e.Property(x => x.EventId).HasColumnName("event_id").IsRequired();
            e.Property(x => x.UserId).HasColumnName("user_id").IsRequired();
            e.Property(x => x.IsDone).HasColumnName("is_done").HasDefaultValue(false).IsRequired();

            e.HasOne<CalendarEvent>()
                .WithMany()
                .HasForeignKey(x => x.EventId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasOne<User>()
                .WithMany()
                .HasForeignKey(x => x.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasIndex(x => x.UserId);
        });

        b.Entity<Notification>(e =>
        {
            e.ToTable("notifications");
            e.HasKey(x => x.Id);

            e.Property(x => x.UserId).HasColumnName("user_id").IsRequired();
            e.Property(x => x.GroupId).HasColumnName("group_id").IsRequired();
            e.Property(x => x.Type).HasColumnName("type").IsRequired().HasMaxLength(100);
            e.Property(x => x.Title).HasColumnName("title").IsRequired().HasMaxLength(50);
            e.Property(x => x.Body).HasColumnName("body").IsRequired().HasMaxLength(3000);
            e.Property(x => x.CreatedAt).HasColumnName("created_at").IsRequired();
            e.Property(x => x.SentAt).HasColumnName("sent_at");
            e.Property(x => x.SendAttempts).HasColumnName("send_attempts").IsRequired().HasDefaultValue(0);

            e.HasOne<User>()
                .WithMany()
                .HasForeignKey(x => x.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasOne<Group>()
                .WithMany()
                .HasForeignKey(x => x.GroupId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasIndex(x => x.UserId);
            e.HasIndex(x => x.GroupId);
            e.HasIndex(x => x.CreatedAt);
        });

        b.Entity<UserDevice>(e =>
        {
            e.ToTable("user_devices");
            e.HasKey(x => x.Id);

            e.Property(x => x.UserId).HasColumnName("user_id").IsRequired();
            e.Property(x => x.Platform).HasColumnName("platform").IsRequired();
            e.Property(x => x.Token).HasColumnName("token").IsRequired().HasMaxLength(512);
            e.Property(x => x.CreatedAt).HasColumnName("created_at").IsRequired();
            e.Property(x => x.LastUsedAt).HasColumnName("last_used_at");
            e.Property(x => x.IsActive).HasColumnName("is_active").IsRequired().HasDefaultValue(true);

            e.HasOne<User>()
                .WithMany()
                .HasForeignKey(x => x.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasIndex(x => new { x.UserId, x.IsActive });
            e.HasIndex(x => x.Token).IsUnique();
        });

        b.Entity<News>(e =>
        {
            e.ToTable("news");
            e.HasKey(x => x.Id);

            e.Property(x => x.GroupId).HasColumnName("group_id").IsRequired();
            e.Property(x => x.GroupName).HasColumnName("group_name").IsRequired().HasMaxLength(50);
            e.Property(x => x.Section).HasColumnName("section").IsRequired().HasMaxLength(50);
            e.Property(x => x.Detail).HasColumnName("detail").IsRequired().HasMaxLength(3000);
            e.Property(x => x.Type).HasColumnName("type").IsRequired().HasMaxLength(100);
            e.Property(x => x.CreatedAt).HasColumnName("created_at").IsRequired();

            e.HasOne<Group>()
                .WithMany()
                .HasForeignKey(x => x.GroupId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasIndex(x => x.GroupId);
            e.HasIndex(x => x.CreatedAt);
        });

        b.Entity<NewsRead>(e =>
        {
            e.ToTable("news_reads");
            e.HasKey(x => new { x.UserId, x.NewsId });

            e.Property(x => x.UserId).HasColumnName("user_id").IsRequired();
            e.Property(x => x.NewsId).HasColumnName("news_id").IsRequired();
            e.Property(x => x.ReadAt).HasColumnName("read_at").IsRequired();

            e.HasOne(x => x.User)
                .WithMany()
                .HasForeignKey(x => x.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasOne(x => x.News)
                .WithMany()
                .HasForeignKey(x => x.NewsId)
                .OnDelete(DeleteBehavior.Cascade);

            e.HasIndex(x => x.NewsId);
        });
    }
}
