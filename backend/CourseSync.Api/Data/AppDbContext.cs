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
    }
}
